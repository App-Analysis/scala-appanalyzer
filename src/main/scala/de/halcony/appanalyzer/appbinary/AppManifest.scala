package de.halcony.appanalyzer.appbinary

import de.halcony.appanalyzer.AppAnalyzer.{error, info}
import de.halcony.appanalyzer.{Config, appbinary}
import de.halcony.appanalyzer.platform.PlatformOS
import de.halcony.appanalyzer.platform.PlatformOS.{Android, PlatformOS, iOS}
import de.halcony.argparse.{Parser, ParsingResult}
import spray.json.{JsObject, JsString, JsonParser}
import wvlet.log.LogSupport

import java.io.{File, FileWriter}
import scala.collection.mutable
import scala.io.Source
import scala.collection.mutable.{Map => MMap}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration.Inf
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}

class AppManifest extends LogSupport {

  private val manifest : MMap[String, MobileApp] = mutable.Map()

  def addApp(appId : String, app : MobileApp, force : Boolean = false) : Boolean = {
    if (!manifest.contains(appId) || force) {
      manifest.addOne(appId -> app)
      true
    } else {
      false
    }
  }

  def removeApp(appId : String) : Boolean = {
    if(!manifest.contains(appId)) {
      warn(s"there is no app $appId in the manifest to be removed")
      true
    } else {
      manifest.remove(appId)
      true
    }
  }

  def writeManifestFile(manifestFilePath : String) : Unit = {
    val file = new FileWriter(new File(manifestFilePath))
    try {
      file.write(JsObject(this.getManifest.map { case (path, app) =>
        path -> JsObject(
          "id" -> JsString(app.id),
          "version" -> JsString(app.version),
          "os" -> JsString(app.getOsString),
          "path" -> JsString(app.serializedPath)
        )
      }).prettyPrint)
    } finally {
      file.flush()
      file.close()
    }
  }

  def getManifest : Map[String,MobileApp] = manifest.toMap

}

object AppManifest extends LogSupport {

  val parser : Parser = Parser("manifest")
    .addSubparser(Parser("update", "updates the manifest file for the provided app data collection")
      .addDefault[(ParsingResult, Config) => Unit]("func",updateOrCreateManifestMain))

  private def updateOrCreateManifestMain(pargs : ParsingResult, conf : Config) : Unit = {
    val appFolder : String = pargs.getValue[String]("path")
    val manifestFilePath = pargs.getValueOrElse[String]("manifest",appFolder + "/manifest.json")
    val platform : PlatformOS = pargs.getValue[String]("platform") match {
      case "android_device" | "android_device_non_root" | "android_emulator" => Android
      case "ios" => iOS
      case x => throw new RuntimeException(s"the mobile operating system $x is unknown and not supported")
    }
    AppManifest(appFolder, manifestFilePath, platform, update = true).writeManifestFile(manifestFilePath)
  }

  def readInFolder(path  : String, device : PlatformOS) : List[MobileApp] = {
    val future = Future.sequence {
      getApps(path, device).map {
        appBinaryPath => Future { anlyzeAppBinary(appBinaryPath, device) }
      }
    }
    Await.result(future,Inf).filter {
      case Failure(exception) =>
        error(exception.getMessage)
        false
      case Success(_) =>
        true
    }.map(_.get)
  }

  def anlyzeAppBinary(path : String, device : PlatformOS) : Try[MobileApp] = {
    device match {
      case Android => analyzeApk(path)
      case iOS => throw new NotImplementedError() //todo: at some point we might want to do iOS again
    }
  }

  protected def analyzeApk(path : String) : Try[MobileApp] = {
    try {
      val id : String = ???
      val version  : String = ???
      Success(MobileApp(id, version, Android, path))
    } catch {
      case x : Throwable => Failure(x)
    }
  }



  protected def getApps(path : String, device : PlatformOS) : List[String] = {
    def getFilesOrFileEndingWith(path : String, suffix : String) : List[String] = {
      if (new File(path).isDirectory) {
        new File(path)
          .listFiles()
          .filter(_.isFile)
          .filter(_.getPath.endsWith(suffix))
          .map(_.getPath)
          .toList
      } else {
        if(!path.endsWith(suffix)) {
          throw new RuntimeException(s"if you do not provide a folder of files the file needs to end with '$suffix'")
        } else {
          List(path)
        }
      }
    }
    device match {
      case Android => getFilesOrFileEndingWith(path,".apk")
      case iOS => getFilesOrFileEndingWith(path,".ipa")
    }
  }

  private def readManifest(manifestFilePath : String) : Map[String, MobileApp] = {
    if (new File(manifestFilePath).exists) {
      info("detected app manifest")
      val source = Source.fromFile(manifestFilePath)
      try {
        JsonParser(source.getLines().mkString("\n"))
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
                  case "android" => PlatformOS.Android
                  case "ios"     => PlatformOS.iOS
                },
                app.fields("path").asInstanceOf[JsString].value
              )
            case _ =>
              error("manifest seems broken")
              "NO" -> appbinary.MobileApp("NO", "NO", Android, "NO")
          }
          .filter { case (path, _) => path != "NO" }
      } finally {
        source.close()
      }
    } else {
      Map()
    }
  }

  def apply(appFolderPath : String, platform : PlatformOS, update : Boolean) : AppManifest = {
    AppManifest(appFolderPath, appFolderPath + "manifest.json", platform, update)
  }

  def apply(appFolderPath : String, manifestFilePath : String, platform : PlatformOS, update : Boolean) : AppManifest = {
    val manifest = new AppManifest()
    readManifest(manifestFilePath).foreach{case (name,app) => manifest.addApp(name, app)}
    if(manifest.getManifest.isEmpty || update) {
      val contained = readInFolder(appFolderPath, platform).map {
        app =>
          manifest.addApp(app.id, app)
          app.id
      }
      manifest.getManifest.keySet.filterNot(contained.contains).foreach(manifest.removeApp)
    }
    manifest
  }

}
