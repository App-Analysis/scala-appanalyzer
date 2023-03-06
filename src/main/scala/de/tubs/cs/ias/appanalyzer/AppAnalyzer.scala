package de.tubs.cs.ias.appanalyzer

import com.mchange.v3.concurrent.BoundedExecutorService
import de.halcony.argparse.{OptionalValue, Parser, ParsingException, ParsingResult}
import de.tubs.cs.ias.appanalyzer.analysis.Analysis
import de.tubs.cs.ias.appanalyzer.analysis.actors.consent.ConsentDialogAcceptRejectActor
import de.tubs.cs.ias.appanalyzer.analysis.actors.{Actor, DoNothingActor, SimpleTrafficCollection}
import de.tubs.cs.ias.appanalyzer.appbinary.{MobileApp, apk, ipa}
import de.tubs.cs.ias.appanalyzer.database.Postgres
import de.tubs.cs.ias.appanalyzer.platform.PlatformOS
import de.tubs.cs.ias.appanalyzer.platform.PlatformOS.{Android, PlatformOS}
import de.tubs.cs.ias.appanalyzer.platform.appium.Appium
import de.tubs.cs.ias.appanalyzer.platform.device.{AndroidDevice, Device, iOSDevice}
import de.tubs.cs.ias.appanalyzer.platform.exceptions.FatalError
import scalikejdbc.scalikejdbcSQLInterpolationImplicitDef
import spray.json.{JsArray, JsNumber, JsObject, JsString, JsonParser}
import wvlet.log.LogSupport

import java.io.{File, FileWriter}
import java.util.concurrent.Executors
import scala.collection.mutable.{Map => MMap}
import scala.concurrent.duration.Duration.Inf
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutorService, Future}
import scala.io.Source
import scala.io.StdIn.readLine

object AppAnalyzer extends LogSupport {

  private val executorService = new BoundedExecutorService(
    Executors.newFixedThreadPool(10), // a pool of ten Threads
    100, // block new tasks when 100 are in process
    50 // restart accepting tasks when the number of in-process tasks falls below 50
  )
  private implicit val executionContext: ExecutionContextExecutorService = ExecutionContext.fromExecutorService(executorService)

  val parser: Parser = Parser("AppAnalyzer",
    "run apps and analyze their consent dialogs")
    .addOptional("config",
      "c",
      "config",
      Some("./config.json"),
      " the configuration file")
    .addPositional(
      "platform",
      "the platform to be analyzed [android_device,android_emulator,ios]")
    .addPositional("path", "path to the required data for the chosen action")
    .addFlag("ephemeral", "e", "ephemeral", "if set the experiment will be deleted directly after execution")
    .addOptional("batchSize", "b", "batch", None, "limit the amount of apps that are analyzed in bulk")
    .addOptional("continue", "r", "resume", None, "provides the experiment to continue")
    .addSubparser(Parser("removedFailedAnalysis", "delete listed analysis ids")
      .addDefault[(ParsingResult,Config) => Unit]("func", deleteAnalysisMain)
    )
    .addSubparser(Parser("extractFileData","extracts the files used in the provided binaries")
      .addDefault[(ParsingResult,Config) => Unit]("func",extractFilesMain))
    .addSubparser(Parser("functionalityCheck", "run through all fundamental API actions to check if it works")
      .addDefault[(ParsingResult, Config) => Unit]("func", functionalityCheck)
    )
    .addSubparser(Parser("debug","starts the app, dummy traffic collection, and appium")
      .addDefault[String]("description", "navigating to login dialog")
      .addDefault[(ParsingResult, Config) => Unit]("func", debugMain))
    .addSubparser(
      Parser("trafficCollection", "collect traffic while an app is running")
        .addFlag("infinity","i","infinity","if set the traffic collection will run until aborted by the user")
        .addDefault[String]("description", "basic traffic collection")
        .addDefault[(ParsingResult, Config) => Unit]("func", trafficCollectionMain))
    .addSubparser(
      Parser("consentDialogAnalysis", "analyze the structure and behavior of consent dialogs")
        .addFlag("noTrafficCollection","n","no-traffic-collection",
          "if set there will be no traffic collection")
        .addSubparser(Parser("simpleAnalysis","start the app and collect the dialog")
          .addDefault[(ParsingResult, Config) => Unit]("func", consentDialogAnalysisSimpleMain)
          .addDefault[String]("description", "analyze consent dialog"))
        .addSubparser(Parser("rejectAnalysis", "start the app and collect the dialog")
          .addDefault[(ParsingResult, Config) => Unit]("func", consentDialogAnalysisRejectMain)
          .addDefault[String]("description", "reject consent dialog"))
        .addSubparser(Parser("acceptAnalysis", "start the app and collect the dialog")
          .addDefault[(ParsingResult, Config) => Unit]("func", consentDialogAnalysisAcceptMain)
          .addDefault[String]("description", "accept consent dialog"))
        .addSubparser(Parser("fullAnalysis", "start the app and collect the dialog")
          .addDefault[(ParsingResult, Config) => Unit]("func", consentDialogAnalysisFullMain)
          .addDefault[String]("description", "full consent dialog analysis")))



  /** main function parsing config and command line args
   *
   * @param args the command line args provided by the jvm
   */
  def main(args: Array[String]): Unit = {
    try {
      val pargs = parser.parse(args)
      val conf = Config.parse(pargs.getValue[String]("config"))
      pargs.getValue[(ParsingResult, Config) => Unit]("func")(pargs, conf)
    } catch {
      case _: ParsingException =>
      case x: Throwable =>
        println(x)
    }
  }

  def getBatchSize(pargs : ParsingResult) : Option[Int] = {
    try {
      Some(pargs.getValue[String]("batchSize").toInt)
    } catch {
      case _ : Throwable => None
    }
  }

  def deleteAnalysisMain(pargs : ParsingResult, conf : Config) : Unit = {
    Postgres.initializeConnectionPool(conf)
    val delete = Source.fromFile(pargs.getValue[String]("path"))
    try {
      val json : List[Int] = JsonParser(delete.getLines().mkString("\n"))
        .asJsObject.fields("_failed")
        .asInstanceOf[JsArray].elements.map(_.asInstanceOf[JsNumber].value.toInt).toList
      println(s"shall we really delete those [${json.length}] analysis: ${json.mkString(",")}")
      val userInput = readLine("[Yes/No]")
      if(userInput.trim.toLowerCase == "yes") {
        Postgres.withDatabaseSession {
          implicit session =>
            json.foreach {
              id =>
                info(s"deleting $id")
                sql"""DELETE FROM interfaceanalysis WHERE id = $id"""
                  .update.apply()
            }
        }
      }
    } finally {
      delete.close()
    }
  }



  private def getDevice(pargs: ParsingResult, conf: Config): Device = {
    pargs.getValue[String]("platform") match {
      case "android_device" => AndroidDevice(conf)
      case "ios" => iOSDevice(conf)
      case x =>
        throw new RuntimeException(s"device type $x is not yet supported")
    }
  }

  private def readManifestFile(path : String) : Map[String,MobileApp] = {
    if (new File(path).exists) {
      info("detected app manifest")
      val source = Source.fromFile(path)
      try {
        JsonParser(source.getLines().mkString("\n")).asInstanceOf[JsObject].fields.map {
          case (path : String, app : JsObject) =>
            path -> MobileApp(
              app.fields("id").asInstanceOf[JsString].value,
              app.fields("version").asInstanceOf[JsString].value,
              app.fields("os").asInstanceOf[JsString].value.toLowerCase match {
                case "android" => PlatformOS.Android
                case "ios" => PlatformOS.iOS
              },
              app.fields("path").asInstanceOf[JsString].value,
            )
          case _ =>
            error("manifest seems broken")
            "NO" -> MobileApp("NO","NO",Android,"NO")
        }.filter{case (path,_) => path != "NO"}
      } finally {
        source.close()
      }
    } else {
      Map()
    }
  }

  private def writeManifestFile(path : String, apps : Map[String,MobileApp]) : Unit = {
    val file = new FileWriter(new File(path))
    try {
      file.write(JsObject(apps.map {
        case (path,app) =>
          path -> JsObject(
            "id" -> JsString(app.id),
            "version" -> JsString(app.version),
            "os" -> JsString(app.getOsString),
            "path" -> JsString(app.path)
          )
      }).prettyPrint)
    } finally {
      file.flush()
      file.close()
    }
  }

  /** filters the apps contained in the folder by already analyzed apps and creates MobileApp objects
   *
   * @param appPaths the paths to all the relevant app packages
   * @param conf the configuration
   * @param os the operating system for which the apps are
   * @return a list of MobileApp objects
   */
  private def filterAppsInFolder(folderPath : String, appPaths: List[String], conf : Config, os : PlatformOS, filtering : Boolean) : List[MobileApp] = {
    val manifestFilePath = s"$folderPath/manifest.json"
    val manifest = MMap(readManifestFile(manifestFilePath).toList :_*)
    val inspector = os match {
      case Android => apk.APK(conf)
      case PlatformOS.iOS => ipa.IPA(conf)
    }
    val alreadyChecked : Set[String] = if(filtering) Experiment.getAnalyzedApps.map(_.id).toSet else Set()
    val appFuture = Future.sequence {
      appPaths.map {
        path =>
          Future {
            try {
              val app = manifest.synchronized(manifest.get(path)) match {
                case Some(app) => app
                case None =>
                  val app = appbinary.MobileApp(inspector.getAppId(appbinary.MobileApp("", "", os, path)), "NA", os, path)
                  manifest.synchronized(manifest.addOne(path -> app))
                  app
              }
              if(alreadyChecked.contains(app.id)) {
                None
              } else {
                Some(app)
              }
            } catch {
              case x: Throwable =>
                error(x.getMessage)
                None
            }
          }
      }
    }
    val ret = Await.result(appFuture,Inf).filter(_.nonEmpty).map(_.get)
    writeManifestFile(manifestFilePath,manifest.toMap)
    ret
  }

  /** takes the provided path and returns the limited subset of all contained apps
   *
   * @param pargs the parsed command line arguments
   * @param device the device to be used
   * @param conf the parsed config
   * @return a list of mobile apps contained in the path
   */
  private def getRelevantApps(pargs : ParsingResult, device : Device, conf : Config, filtering : Boolean = true) : List[MobileApp] = {
    val path = pargs.getValue[String]("path")
    val apps = device.PLATFORM_OS match {
      case Android =>
        val (apks,folder) = if(new File(path).isDirectory) {
          (new File(path).listFiles().filter(_.isFile).filter(_.getPath.endsWith(".apk")).map(_.getPath).toList,path)
        }  else {
          assert(path.endsWith(".apk"), s"path has to end with apk if not a directory in $path")
          (List(path),new File(path).getParentFile.getPath)
        }
        filterAppsInFolder(folder,apks,conf,Android,filtering)
      case PlatformOS.iOS =>
        val (ipas,folder) : (List[String],String) = if(new File(path).isDirectory) {
          (new File(path).listFiles().filter(_.isFile).filter(_.getPath.endsWith(".ipa")).map(_.getPath).toList,path)
        } else {
          assert(path.endsWith(".ipa"), s"path has to end with ipa if not a directory in $path")
          (List(path), new File(path).getParentFile.getPath)
        }
        filterAppsInFolder(folder,ipas,conf,PlatformOS.iOS,filtering)

    }
    apps.slice(0,getBatchSize(pargs).getOrElse(apps.length))
  }


  /** wrapper to run arbitrary analysis for all apps contained in the batch with a possible resume flag
   *
   * @param actor a function creating the actor for reach analysis
   * @param pargs the parsed command line arguments
   * @param conf the configuration
   */
  private def runExperiment(actor : => Actor, pargs: ParsingResult, conf: Config, empty : Boolean = false) : Unit = {
    val device = getDevice(pargs, conf)
    Postgres.initializeConnectionPool(conf)
    val description = pargs.getValue[String]("description")
    info(s"running $description")
    pargs.get[OptionalValue[String]]("continue").value match {
      case Some(value) => Experiment.loadExperiment(value.toInt)
      case None => Experiment.createNewExperiment(description)
    }
    try {
      if(!empty) {
        val apps = getRelevantApps(pargs, device, conf)
        var counter = apps.length
        apps.foreach {
          app =>
            info(s"we have $counter app${if (counter > 1) "s" else ""} to analyze")
            Analysis.runAnalysis(actor, app, device, conf)
            counter = counter - 1
        }
      } else {
        Analysis.runAnalysis(actor, MobileApp("EMPTY","EMPTY",device.PLATFORM_OS,"EMPTY"), device, conf)
      }
    } catch {
      case x : FatalError =>
        error(x.getMessage)
      case x : Throwable =>
        error(s"${x.getMessage} \n ${x.getStackTrace.mkString("\n")}")
        Experiment.addEncounteredError(x)
    } finally {
      device.resetDevice()
      if (pargs.getValue[Boolean]("ephemeral")) {
        Experiment.deleteCurrentExperiment()
        info("ephemeral experiment is done")
      } else {
        info(s"experiment ${Experiment.getCurrentExperiment.id} is done")
      }
    }
  }

  private def trafficCollectionMain(pargs: ParsingResult, conf: Config): Unit = {
    val timems: Long = if(pargs.getValue[Boolean]("infinity")) -1 else conf.initialCollectionTimeMilli
    runExperiment(new SimpleTrafficCollection(timems),pargs,conf, empty = true)
  }

  private def consentDialogAnalysisSimpleMain(pargs: ParsingResult, conf: Config): Unit = {
    runExperiment(new ConsentDialogAcceptRejectActor(false,false,false,conf),pargs,conf)
  }

  private def consentDialogAnalysisRejectMain(pargs: ParsingResult, conf: Config): Unit = {
    runExperiment(new ConsentDialogAcceptRejectActor(true,false,
      !pargs.getValue[Boolean]("noTrafficCollection"),conf),pargs,conf)
  }

  private def consentDialogAnalysisAcceptMain(pargs: ParsingResult, conf: Config): Unit = {
    runExperiment(new ConsentDialogAcceptRejectActor(false,true,
      !pargs.getValue[Boolean]("noTrafficCollection"),conf),pargs,conf)
  }

  private def consentDialogAnalysisFullMain(pargs: ParsingResult, conf: Config): Unit = {
    runExperiment(new ConsentDialogAcceptRejectActor(true,true,
      !pargs.getValue[Boolean]("noTrafficCollection"),conf),pargs,conf)
  }

  private def debugMain(pargs : ParsingResult, conf : Config) : Unit = {
    runExperiment(new DoNothingActor(),pargs,conf)
  }

  private def extractFilesMain(pargs : ParsingResult, conf : Config) : Unit = {
    try {
      val device = getDevice(pargs, conf)
      val analyzer = device.getAppPackageAnalysis(conf)
      val apps: Seq[MobileApp] = getRelevantApps(pargs, device, conf,filtering = false)
      info(s"about to extract binary information for ${apps.length} apps")
      def prettyPrintOnDemandObjectElement(key : String, value : List[String]) : Unit = synchronized {
        println(s"""$key, ${value.mkString(",")}""")
      }
      val future = Future.sequence {
        apps.map {
          app => Future{
            prettyPrintOnDemandObjectElement(app.toString,analyzer.getIncludedFiles(app.path))
          }
        }
      }
      Await.ready(future,Inf)
      info("done")
    } catch {
      case x : Throwable =>
        error(x.getMessage)
        error(x.getStackTrace.mkString("\n"))
    }
  }

  /** perform functionality check of the currently connect device
   *
   * @param pargs the parsed command line arguments
   * @param conf the parsed config file
   */
  private def functionalityCheck(pargs : ParsingResult, conf : Config) : Unit = {
    println("Welcome to the functionality check!")
    println()
    println("This is most likely the most important yet most frustrating feature of all.")
    println("We will go through each element of the Device and Appium API to check if it works with the currently")
    println("attached and configured device.")
    println("As the path parameter I expect a single app")
    println("If something fails I might give some useful hints or just crash ¯\\_(oo)_/¯.")
    println("Let's start...")

    val device = getDevice(pargs,conf)
    val path : String = pargs.getValue[String]("path")
    println(s"We are supposed to work on ${device.PLATFORM_OS}")
    println(s"As our Canary App we are using $path")

    def tryApiCommand(description : String, failureHint : Option[String] = None, optional : Boolean = false)(func : => Option[String]): Unit ={
      try {
        println(s"action: $description")
        func match {
          case Some(value) => println(s"return: $value")
          case None =>
        }
      } catch {
        case x : Throwable =>
          println(s"Failed with: ${x.getMessage}")
          println(x.getStackTrace.mkString("\n"))
          failureHint match {
            case Some(value) => println(s"Hint: $value")
            case None =>
          }
          if(!optional) throw new RuntimeException(s"$description failed, no point in continuing")
      }
      println()
    }

    tryApiCommand("device.ensureDevice") {
      device.ensureDevice()
      None
    }

    tryApiCommand("device.startFrida") {
      device.startFrida()
      None
    }

    tryApiCommand("device.restartPhone") {
      None
    }

    tryApiCommand("device.getAppPackageAnalysis") {
      device.getAppPackageAnalysis(conf)
      None
    }

    val app = appbinary.MobileApp("","",PlatformOS.iOS,path)

    tryApiCommand("appPackageAnalysis.getAppId") {
      Some(device.getAppPackageAnalysis(conf).getAppId(app))
    }

    tryApiCommand("installApp") {
      device.installApp(app)
      None
    }

    val appId = device.getAppPackageAnalysis(conf).getAppId(app)

    tryApiCommand("grantPermissios") {
      device.setAppPermissions(appId)
      None
    }

    tryApiCommand("startApp") {
      device.startApp(appId)
      Some(s"app running: ${device.getForegroundAppId.get == appId}")
    }

    tryApiCommand("getPid") {
      Some(device.getPid(appId))
    }

    tryApiCommand("getPrefs") {
      device.getPrefs(appId)
    }

    tryApiCommand("getPlatformSpecificData") {
      device.getPlatformSpecificData(appId)
    }

    println("seems like everything is in working order")
    println("now we are going into some more involved features corresponding to our promised functionalities")

    // using appium to retrieve elements (which implies that we can also interact)
    tryApiCommand("appium.getElements") {
      Appium.withRunningAppium(appId, conf,device) {
        appium =>
          Some(appium.findElementsByXPath("//*").map(_.getText.trim).filter(_ != "").mkString("\n"))
      }
    }

    // everything works as intended
    tryApiCommand("stopApp") {
      device.closeApp(appId)
      Some(s"app running: ${device.getForegroundAppId.getOrElse("nobody") == appId}")
    }


    tryApiCommand("device.stopFrida") {
      device.stopFrida()
      None
    }

    tryApiCommand("uninstallApp") {
      device.uninstallApp(appId)
      None
    }




  }

}
