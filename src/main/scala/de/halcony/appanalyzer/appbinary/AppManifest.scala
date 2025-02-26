package de.halcony.appanalyzer.appbinary

import de.halcony.appanalyzer.appbinary.ManifestJsonProtocol.ManifestFormat
import de.halcony.appanalyzer.appbinary.apk.APK
import de.halcony.appanalyzer.platform.PlatformOperatingSystems
import de.halcony.appanalyzer.platform.PlatformOperatingSystems.{
  ANDROID,
  IOS,
  PlatformOS
}
import de.halcony.appanalyzer.{Config, appbinary}
import de.halcony.argparse.{OptionalValue, ParsingResult}
import spray.json.{
  DeserializationException,
  JsObject,
  JsString,
  JsonParser,
  enrichAny
}
import wvlet.log.LogSupport

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration.Inf
import scala.concurrent.{Await, Future}
import scala.io.Source
import scala.jdk.CollectionConverters.IteratorHasAsScala
import scala.util.{Failure, Success, Try}

class AppManifest(
    val manifestFilePath: Path,
    var appFolderPath: Path,
    val apps: mutable.Set[MobileApp]
) extends LogSupport {
  def writeManifestToFile(
      manifestFilePath: Path = this.manifestFilePath
  ): Unit = {
    try {
      Files.write(
        manifestFilePath,
        Manifest(this.appFolderPath, this.apps).toJson.prettyPrint
          .getBytes(StandardCharsets.UTF_8)
      )
    } catch {
      case e: Exception =>
        error(s"An error occurred while writing to file ${e.getMessage}")
    }
  }
}

object AppManifest extends LogSupport {
  def writeAppManifestToFile(
      manifest: AppManifest
  ): Unit = {
    try {
      Files.write(
        manifest.manifestFilePath,
        Manifest(manifest.appFolderPath, manifest.apps).toJson.prettyPrint
          .getBytes(StandardCharsets.UTF_8)
      )
    } catch {
      case e: Exception =>
        error(s"An error occurred while writing to file ${e.getMessage}")
    }
  }

  def updateOrCreateManifestMain(
      pargs: ParsingResult,
      conf: Config
  ): Unit = {
    val appFolder: Path = Path.of(pargs.getValue[String]("path"))
    val manifestFilePath =
      pargs.get[OptionalValue[String]]("manifest").value match {
        case Some(value) => Path.of(value)
        case None        => appFolder.resolve("/manifest.json")
      }
    val platform: PlatformOS = pargs.getValue[String]("platform") match {
      case "android_device" | "android_device_non_root" | "android_emulator" =>
        ANDROID
      case "ios" => IOS
      case x =>
        throw new RuntimeException(
          s"the mobile operating system $x is unknown and not supported"
        )
    }
    info(s"creating manifest file $manifestFilePath for $appFolder")
    AppManifest(appFolder, manifestFilePath, platform, update = true)(conf)
      .writeManifestToFile()
  }

  private def readInFolder(path: Path, device: PlatformOS)(implicit
      conf: Config
  ): mutable.Set[MobileApp] = {
    val appPaths = getApps(path, device)
    info(s"we detected ${appPaths.length} app files in the provided folder")
    val future = Future.sequence {
      appPaths.map { appBinaryPath =>
        Future { analyzeAppBinary(appBinaryPath, device)(conf) }
      }
    }
    Await
      .result(future, Inf)
      .filter {
        case Failure(exception) =>
          error(exception.getMessage)
          false
        case Success(_) =>
          true
      }
      .map(_.get)
      .to(mutable.Set)
  }

  private def analyzeAppBinary(
      path: String,
      device: PlatformOperatingSystems.PlatformOS
  )(implicit
      conf: Config
  ): Try[MobileApp] = {
    device match {
      case PlatformOperatingSystems.IOS =>
        Failure(
          new NotImplementedError()
        ) // todo: at some point we might want to do iOS again
      case PlatformOperatingSystems.ANDROID => analyzeApk(Path.of(path))
    }
  }

  protected def analyzeApk(
      path: Path
  )(implicit conf: Config): Try[MobileApp] = {
    try {
      val id: String = APK(conf).getAppId(path.toString)
      val version: String = APK(conf).getAppVersion(path.toString)
      Success(MobileApp(id, version, ANDROID, path))
    } catch {
      case x: Throwable => Failure(x)
    }
  }

  protected def getApps(path: Path, device: PlatformOS): List[String] = {
    def getFilesOrFileEndingWith(path: Path, suffix: String): List[String] = {
      if (Files.isDirectory(path)) {
        val stream = Files.list(path)
        try {
          // Filter the files
          stream
            .filter(Files.isRegularFile(_)) // Keep only regular files
            .filter(filePath =>
              filePath.getFileName.toString.endsWith(".stl")
            ) // Check suffix
            .map(_.toString) // Convert Path to String
            .iterator() // Convert Stream[Path] to an Iterator
            .asScala // Convert to a Scala collection for compatibility
            .toList // Collect to List
        } finally {
          stream.close() // Ensure the stream is closed
        }
      } else {
        if (!path.endsWith(suffix)) {
          throw new RuntimeException(
            s"if you do not provide a folder of files the file needs to end with '$suffix'"
          )
        } else {
          List(path.toString)
        }
      }
    }
    device match {
      case ANDROID => getFilesOrFileEndingWith(path, ".apk")
      case IOS     => getFilesOrFileEndingWith(path, ".ipa")
    }
  }

  private def readManifest(
      appFolderPath: Path,
      manifestFilePath: Path
  ): AppManifest = {
    var manifest: AppManifest =
      new AppManifest(manifestFilePath, appFolderPath, mutable.Set())
    if (Files.exists(manifestFilePath)) {
      info("detected app manifest")
      val source = Source.fromFile(manifestFilePath.toString)

      info("reading in lines")
      var lines = ""
      try {
        lines = source.getLines().mkString("\n")
      } catch {
        case _: Throwable =>
          error("could not read in lines")
      } finally {
        source.close()
      }

      info("try parsing to manifest json")
      try {
        val manifestFile = JsonParser(lines).asInstanceOf[Manifest]
        manifest = new AppManifest(
          manifestFilePath,
          manifestFile.appFolderPath,
          manifestFile.apps
        )
        sanityCheck(manifest)
      } catch {
        case _: DeserializationException =>
          info("could not parse to manifest json, retrying with legacy format")
          val app_list: Map[String, MobileApp] = readLegacyManifest(lines)
          manifest = new AppManifest(
            manifestFilePath,
            appFolderPath,
            app_list.values.to(mutable.Set)
          )
          sanityCheck(manifest)
      }
    }
    manifest
  }

  // do we need path -> app mapping? is this replaced by manifest.path?
  private def readLegacyManifest(data: String): Map[String, MobileApp] = {
    try {
      JsonParser(data)
        .asInstanceOf[JsObject]
        .fields
        .map {
          case (path: String, app: JsObject) =>
            path -> appbinary.MobileApp(
              app.fields("id").asInstanceOf[JsString].value,
              app.fields("version").asInstanceOf[JsString].value,
              app
                .fields("os")
                .asInstanceOf[JsString]
                .value
                .toLowerCase match {
                case "android" => PlatformOperatingSystems.ANDROID
                case "ios"     => PlatformOperatingSystems.IOS
              },
              Path.of(app.fields("path").asInstanceOf[JsString].value)
            )
          case _ =>
            error("manifest seems broken")
            "NO" -> appbinary.MobileApp("NO", "NO", ANDROID, Path.of("NO"))
        }
        .filter { case (path, _) => path != "NO" }
    } catch {
      case _: Throwable =>
        error("could not parse legacy manifest")
        Map[String, MobileApp]()
    }
  }

  def sanityCheck(manifest: AppManifest): Unit = {
    val appFolderPathNormalized = manifest.appFolderPath.normalize()
    manifest.apps.foreach(app => {
      val appPath = app.path.normalize()
      if (!appPath.startsWith(appFolderPathNormalized)) {
        throw new RuntimeException(
          s"the app path $appPath is not a sub-path of the app folder path $appFolderPathNormalized"
        )
      }
    })
  }

  def apply(appFolderPath: Path, apps: mutable.Set[MobileApp]): AppManifest = {
    new AppManifest(
      appFolderPath.resolve("/manifest.json"),
      appFolderPath,
      apps
    )
  }

  def apply(appFolderPath: Path, platform: PlatformOS, update: Boolean)(implicit
      conf: Config
  ): AppManifest = {
    AppManifest(
      appFolderPath,
      appFolderPath.resolve("/manifest.json"),
      platform,
      update
    )
  }

  def apply(
      appFolderPath: Path,
      manifestFilePath: Path,
      platform: PlatformOS,
      update: Boolean
  )(implicit conf: Config): AppManifest = {
    val manifest = readManifest(appFolderPath, manifestFilePath)
    manifest.appFolderPath = appFolderPath
    if (manifest.apps.isEmpty || update) {
      if (manifest.apps.isEmpty)
        info(s"the manifest was empty, running update based on app folder")
      else
        info("we force an update based on the app folder")

      if (manifest.appFolderPath != appFolderPath) {
        saveOldManifest(manifest)
        info("setting new appFolderPath and clearing apps")
        manifest.appFolderPath = appFolderPath
        manifest.apps.clear()
      }
      val contained = readInFolder(appFolderPath, platform)
      manifest.apps ++= contained
      manifest.apps.diff(contained).foreach(manifest.apps.remove)
    }
    manifest
  }

  private def saveOldManifest(manifest: AppManifest): Unit = {
    val currentTime = LocalDateTime.now()
    val currentTimeFormatter = {
      DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
    }
    val manifestPath = manifest.appFolderPath.resolve(
      s"manifest_${currentTime.format(currentTimeFormatter)}.json"
    )
    warn(
      s"the app folder path in the manifest is different from the provided app folder path, dumping the old manifest to $manifestPath"
    )
    manifest.writeManifestToFile(manifestPath)
  }
}
