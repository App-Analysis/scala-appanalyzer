package de.halcony.appanalyzer.platform.device

import de.halcony.appanalyzer.Config
import de.halcony.appanalyzer.appbinary.{Analysis, MobileApp}
import de.halcony.appanalyzer.platform.exceptions.FatalError
import de.halcony.appanalyzer.platform.PlatformOperatingSystems.PlatformOS
import spray.json.{
  DefaultJsonProtocol,
  JsObject,
  JsString,
  JsonParser,
  ParserInput,
  RootJsonFormat
}
import wvlet.log.LogSupport

import scala.sys.process._
import java.util.Base64

trait Device extends LogSupport {

  private var failedInteractions: Int = 0
  private val REBOOT_THRESHOLD: Int = 5000
  private val FATAL_ERROR_THRESHOLD: Int = 1000
  val EMULATOR: Boolean
  val ROOT: Boolean
  var initiallyInstalledApps: Option[Set[String]] = None

  protected def increaseFailedInteractions(): Unit = {
    failedInteractions = failedInteractions + 1
    if (failedInteractions == REBOOT_THRESHOLD) {
      warn(s"we had $failedInteractions which reached the reboot threshold")
      if (checkBootState()) {
        restartPhone()
      } else {
        Thread.sleep(
          120000
        ) // no phone boot up should lake longer than 2 minutes
        restartPhone()
      }
    } else if (failedInteractions >= FATAL_ERROR_THRESHOLD) {
      error(
        s"we reached $failedInteractions which passed the fatal threshold ... now I will die"
      )
      throw new FatalError(
        s"we reached $failedInteractions which passed the fatal threshold ... now I will die"
      )
    }
  }

  def startDevice(): Unit = {} // usually we do not need to boot up a device

  def stopDevice(): Unit = {} // usually we do not need to stop a device

  protected def resetFailedInteractions(): Unit = failedInteractions = 0

  val PLATFORM_OS: PlatformOS

  def checkBootState(): Boolean

  def startFrida(): Unit

  def stopFrida(): Unit

  def withRunningFrida[T](func: => T): T

  def restartPhone(): Boolean

  def getAppPackageAnalysis(conf: Config): Analysis

  def ensureDevice(): Unit

  def resetDevice(): Unit

  def clearStuckModals(): Unit

  def installApp(app: MobileApp): Unit

  def uninstallApp(appId: String): Unit

  def startApp(appId: String, retries: Int = 3): Unit

  def closeApp(appId: String): Unit

  def performTouch(x: Int, y: Int): Unit

  def restartApp(appId: String): Unit = {
    closeApp(appId)
    startApp(appId)
  }

  def setAppPermissions(appId: String): Unit

  def resetAndRestartApp(app: MobileApp): Unit = {
    uninstallApp(app.id)
    installApp(app)
    startApp(app.id)
  }

  def getForegroundAppId: Option[String]

  def getPrefs(appId: String): Option[String]

  def getPlatformSpecificData(appId: String): Option[String]

  def getAppVersion(path: String): Option[String]

  def getPid(appId: String): String

  def getInstalledApps: Set[String]

  def runFridaScript(pid: String, script: String): String = {
    val PATH_TO_FRIDA_SCRIPT = "./resources/frida/runFridaScript.js"
    case class Result(result: String)
    case class Error(error: String)

    object HandyJsonReader extends DefaultJsonProtocol {
      implicit val resultFormat: RootJsonFormat[Result] = jsonFormat1(Result)
      implicit val errorFormat: RootJsonFormat[Error] = jsonFormat1(Error)
    }
    import HandyJsonReader._
    val cmd =
      s"node $PATH_TO_FRIDA_SCRIPT $pid ${Base64.getEncoder.encodeToString(script.getBytes)}"
    val res =
      try {
        cmd.!!
      } catch {
        case x: RuntimeException =>
          error(s"running frida script resulted in error: ${x.getMessage}")
          JsObject(
            "error" -> JsString(
              s"ERROR:\n${x.getMessage}\n${x.getStackTrace.mkString("\n")}"
            )
          ).prettyPrint
      }
    try {
      JsonParser(ParserInput(res)).convertTo[Result].result
    } catch {
      case _: Throwable =>
        val msg = JsonParser(ParserInput(res)).convertTo[Error].error
        error("encountered error while executing frida script: " + msg)
        msg
    }
  }

  def assertForegroundApp(appId: String, throwable: Throwable): Unit = {
    getForegroundAppId match {
      case Some(value) =>
        if (value != appId) {
          error(s"foreground app is $value but we expected $appId")
          throw throwable
        }
      case None => throw throwable
    }
  }
}
