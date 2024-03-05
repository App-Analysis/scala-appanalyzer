package de.halcony.appanalyzer.analysis.plugin

import de.halcony.appanalyzer.AppAnalyzer
import de.halcony.argparse.{OptionalValue, Parser, ParsingResult}
import org.clapper.classutil.{ClassFinder, ClassInfo}
import spray.json.{JsArray, JsonParser}
import wvlet.log.LogSupport
import java.io.{File, FileOutputStream}
import java.net.http.HttpResponse.BodyHandlers
import java.net.http.{HttpClient, HttpRequest}
import java.net.{URI, URL, URLClassLoader}
import java.util.jar.JarFile
import scala.annotation.nowarn
import scala.jdk.CollectionConverters.EnumerationHasAsScala

/** Class representing the plugin manager loading and managing plugins
  *
  * @author Simon Koch
  *
  * @param conf the config for the plugin manager
  */
class PluginManager(conf: PluginManagerConfiguration) extends LogSupport {

  /** the directory in which to store the plugins
    */
  private val PLUGIN_DIRECTORY = conf.folder

  /** the fully qualified name of the AnalysisPlugin trait
    */
  private val PLUGIN_INTERFACE =
    "de.halcony.appanalyzer.analysis.plugin.ActorPlugin"

  /** the jars contained in the PLUGIN_DIRECTORY folder on startup
    */
  private val jars: List[File] =
    if (new File(PLUGIN_DIRECTORY).exists()) {
      new File(PLUGIN_DIRECTORY)
        .listFiles()
        .filter(_.getPath.endsWith(".jar"))
        .toList
    } else {
      throw new RuntimeException(
        s"there is no directory $PLUGIN_DIRECTORY, please create the directory with the required read and write access")
    }

  /** a map of the plugins and the name of the main classes
    */
  private val pluginMap: Map[String, (ClassInfo, File)] = {
    jars.flatMap { jar =>
      val finder = ClassFinder(List(jar))
      finder.getClasses().filter(_.interfaces.contains(PLUGIN_INTERFACE)).map {
        info =>
          info.name.split("\\.").last -> (info, jar)
      }
    }.toMap
  }

  /** get all installed plugins
    *
    * @return the set of class names of installed plugins, i.e., the name of the plugin
    */
  def getInstalledPlugins: Set[(String, String)] =
    pluginMap.map {
      case (name, (_, file)) =>
        (name, file.getPath.split("-").last.stripSuffix(".jar"))
    }.toSet

  /** delete the name plugin
    *
    */
  def removePlugin(name: String): Unit = {
    pluginMap(name)._2.delete()
  }

  /** loads all classes of a given jar with specified classloader
    *
    * @param jarFile the jar file to be loaded
    * @param classloader the classloader to be used
    */
  private def loadAllClassesOfJar(jarFile: JarFile,
                                  classloader: ClassLoader): Unit = {
    jarFile.entries().asScala.foreach { entry =>
      if (!entry.isDirectory && entry.getName.endsWith(".class")) {
        val className =
          entry.getName.substring(0, entry.getName.length - 6).replace("/", ".")
        classloader.loadClass(className)
      }
    }
  }

  /** load a specified plugin and return an instance of the class implementing the interface
    *
    * @param name the name of the plugin
    * @return instantiation of the class implementing the plugin interface
    */
  def loadPlugin(name: String, parameter: Map[String, String]): ActorPlugin = {
    val (classInfo, file): (ClassInfo, File) = pluginMap.getOrElse(
      name,
      throw new RuntimeException(s"the plugin $name does not exist")
    )
    val urls: List[URL] = List(file.toURI.toURL)
    // we need the classloader of the main object
    val parentClassLoader =
      AppAnalyzer.getClass.getClassLoader
    val childClassLoader =
      URLClassLoader.newInstance(urls.toArray, parentClassLoader)
    val jarFile: JarFile = new JarFile(file.getPath)
    loadAllClassesOfJar(jarFile, childClassLoader)
    try {
      Class
        .forName(classInfo.name, true, childClassLoader)
        .getDeclaredConstructor()
        .newInstance()
        .asInstanceOf[ActorPlugin]
        .setParameter(parameter)
    } catch {
      case x: NoClassDefFoundError =>
        error(x)
        error(
          s"plugin $name cannot be loaded check the plugin directory and make sure it is contained")
        throw NoSuchPlugin(name, None)
    } finally {
      childClassLoader.close()
    }
  }

}

object PluginManager extends LogSupport {

  /** PluginManager singleton
    */
  private var manager: Option[PluginManager] = None

  trait HasPluginManagerConfiguration {
    def getPluginManagerConfiguration: PluginManagerConfiguration
  }

  /** get singleton instance of plugin manager
    *
    * @param configuration the plugin manager configuration to be used
    * @return the plugin manager instance
    */
  def getPluginManager(
      configuration: HasPluginManagerConfiguration): PluginManager =
    manager match {
      case Some(manager) => manager
      case None =>
        manager = Some(
          new PluginManager(configuration.getPluginManagerConfiguration))
        manager.get
    }

  val parser: Parser = Parser("plugin", "manage the installed plugins")
    .addSubparser(
      Parser("list", "list all installed plugins")
        .addSubparser(
          Parser("installed", "list all installed plugins")
            .addDefault[(ParsingResult, HasPluginManagerConfiguration) => Unit](
              "func",
              listPlugins,
              "list all installed plugins"))
        .addSubparser(
          Parser("available", "list all available plugins")
            .addFlag("all", "a", "all", "if set list all available versions")
            .addOptional("filter",
                         "f",
                         "filter",
                         None,
                         "a regexp to filter plugins")
            .addDefault[(ParsingResult, HasPluginManagerConfiguration) => Unit](
              "func",
              availablePlugins,
              "list all online available plugins"
            )))
    .addSubparser(Parser("install", "install a plugin")
      .addFlag("update",
               "u",
               "update",
               "if set updates the plugin to the most current version")
      .addFlag("force",
               "f",
               "force",
               "if set removes an already installed version of the plugin")
      .addOptional(
        "version",
        "r",
        "revision",
        None,
        "if set will install (if available) the specified version of the plugin")
      .addPositional("plugin", "the name of the plugin")
      .addDefault[(ParsingResult, HasPluginManagerConfiguration) => Unit](
        "func",
        installPlugin,
        "install/update a named plugin"))

  /** main to list all plugins already installed
    *
    * @param pargs the command line arguments
    * @param conf the configuration
    */
  def listPlugins(@nowarn pargs: ParsingResult,
                  conf: HasPluginManagerConfiguration): Unit = {
    val manager = getPluginManager(conf)
    println("Installed Plugins:")
    manager.getInstalledPlugins.foreach {
      case (name, version) => println(s"* $name $version")
    }
  }

  /** utility function to get all releases of a specified remote plugin
    *
    * @param client the http client to use
    * @param remote the remote plugin repository of interest
    * @return the download details
    */
  private def getReleases(
      client: HttpClient,
      remote: RemotePluginConfig): List[RemoteGithubRelease] = {
    val request = HttpRequest.newBuilder(URI.create(
      s"https://api.github.com/repos/${remote.owner}/${remote.repo}/releases"))
    val json = client.send(request.build(), BodyHandlers.ofString()).body()
    val plugins = JsonParser(json)
      .asInstanceOf[JsArray]
      .elements
      .map(elem => RemoteGithubRelease(elem.asJsObject))
      .sorted
      .reverse
    plugins.toList
  }

  /** list all available plugins based on the configured remote repositories
    *
    * @param pargs the parsed command line arguments
    * @param conf  the parsed configuration
    */
  def availablePlugins(pargs: ParsingResult,
                       conf: HasPluginManagerConfiguration): Unit = {
    val client = HttpClient.newHttpClient()
    println("Available Plugins:")
    val filter = pargs.get[OptionalValue[String]]("filter").value match {
      case Some(value) => value.r.unanchored //either restrict to the filter
      case None        => ".*".r.unanchored // or if no filter list everything
    }
    conf.getPluginManagerConfiguration.available
      .filter(plugin => filter.matches(plugin._1))
      .foreach {
        case (name, remote) =>
          val versions = getReleases(client, remote)
          if (pargs.getValue[Boolean]("all")) {
            versions.foreach { version =>
              println(s"+ $name  ${version.getVersion}")
            }
          } else {
            println(s"+ $name ${versions.head.getVersion}")
          }
      }
  }

  def installPlugin(pargs: ParsingResult,
                    conf: HasPluginManagerConfiguration): Unit = {
    val client = HttpClient
      .newBuilder()
      .followRedirects(HttpClient.Redirect.ALWAYS)
      .build()
    val forced = pargs.getValue[Boolean]("force")
    val update = pargs.getValue[Boolean]("update")
    val version = pargs.get[OptionalValue[String]]("version").value
    if (update)
      assert(
        version.isEmpty,
        "you cannot provide a version for update use `install --force --release <version>`")
    val plugin = pargs.getValue[String]("plugin")
    conf.getPluginManagerConfiguration.available.find {
      case (key, _) => plugin.r.unanchored.matches(key)
    } match {
      case None => throw NoSuchPlugin(plugin, version)
      case Some(remoteTarget) =>
        val releases = getReleases(client, remoteTarget._2)
        val target: RemoteGithubRelease = version match {
          case Some(version) =>
            releases
              .find(_.getVersion == version)
              .getOrElse(throw NoSuchPlugin(plugin, Some(version)))
          case None => releases.head
        }
        info(s"installing plugin ${remoteTarget._1} ${target.getVersion}")
        val manager = getPluginManager(conf)
        manager.getInstalledPlugins.find {
          case (name, _) => name == remoteTarget._1
        } match {
          case Some((_, version)) =>
            warn(s"a previous version ($version) is already installed")
            if (forced || update) {
              warn("removing old version")
              manager.removePlugin(remoteTarget._1)
            } else {
              return
            }
          case None =>
        }
        info(s"downloading plugin ${remoteTarget._1} ${target.getVersion}")
        val request =
          HttpRequest.newBuilder().uri(URI.create(target.jarDownloadLink))
        val ret = client.send(request.build(), BodyHandlers.ofByteArray())
        if (ret.statusCode() != 200) {
          throw new RuntimeException(
            s"download jar returned ${ret.statusCode()} for ${target.jarDownloadLink}")
        }
        val jarName = target.jarDownloadLink.split("/").last
        val fileWriter = new FileOutputStream(
          new File(conf.getPluginManagerConfiguration.folder + "/" + jarName)
        )
        try {
          fileWriter.write(ret.body())
        } finally {
          fileWriter.flush()
          fileWriter.close()
        }
    }
  }

}
