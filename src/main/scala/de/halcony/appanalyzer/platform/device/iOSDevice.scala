package de.halcony.appanalyzer.platform.device

import de.halcony.appanalyzer.Config
import de.halcony.appanalyzer.appbinary.ipa.IPA
import de.halcony.appanalyzer.appbinary.{Analysis, MobileApp}
import de.halcony.appanalyzer.platform.frida.{FridaScripts, iOSFridaScripts}
import de.halcony.appanalyzer.platform.PlatformOperatingSystems.{
  PlatformOS,
  IOS
}
import de.halcony.appanalyzer.platform.exceptions.{
  AppClosedItself,
  UnableToInstallApp,
  UnableToUninstallApp
}
import spray.json.{JsArray, JsNumber, JsObject, JsString, JsonParser}
import wvlet.log.LogSupport

import java.time.Instant
import scala.sys.process._

case class iOSDevice(conf: Config) extends Device with LogSupport {

  override val PLATFORM_OS: PlatformOS = IOS
  override val EMULATOR: Boolean = false
  override val ROOT: Boolean = true

  var objection: Option[Process] = None
  private var runningFrida: Option[Process] = None

  /** permission names in the iOS database that need be given
    */
  val PERMISSIONS_GIVE: List[String] = List(
    "kTCCServiceLiverpool",
    "kTCCServiceUbiquity",
    "kTCCServiceCalendar",
    "kTCCServiceAddressBook",
    "kTCCServiceReminders",
    "kTCCServicePhotos",
    "kTCCServiceMediaLibrary",
    "kTCCServiceBluetoothAlways",
    "kTCCServiceMotion",
    "kTCCServiceWillow",
    "kTCCServiceExposureNotification"
  )

  /** permission names in the iOS database that need not be given
    */
  val PERMISSIONS_NOT_GIVE: List[String] = List(
    "kTCCServiceCamera",
    "kTCCServiceMicrophone",
    "kTCCServiceUserTracking"
  )

  /** the location of the permission db on the iPhone (14.5 14.7)
    */
  val DB_LOCATION = "/private/var/mobile/Library/TCC/TCC.db"

  override def startFrida(): Unit = {
    if (runningFrida.nonEmpty) {
      warn("Frida already running, cannot be started twice")
    } else {
      // frida is running by default (Cydia)
      runningFrida = Some(null)
    }
  }

  override def startDevice()
      : Unit = {} // the iPhone is supposed to be already on

  override def stopFrida(): Unit = {
    runningFrida match {
      case Some(_) =>
        // frida is running by default on the iphone (Cydia)
        runningFrida = None
      case None =>
        warn("trying to destroy a non existent frida instance")
    }
  }

  override def withRunningFrida[T](func: => T): T = {
    func // as frida is already running on the device, this should suffice
  }

  override def restartPhone(): Boolean = {
    // we cannot restart an iPhone.
    warn(
      "fake restarting iPhone, just a 2 minute sleep for everything to calm down"
    )
    Thread.sleep(120000)
    false
  }

  override def getAppPackageAnalysis(conf: Config): Analysis = IPA(conf)

  override def ensureDevice(): Unit = {
    if (s"${conf.ios.ideviceinfo}".!!.split("\n").length <= 1) {
      throw new RuntimeException("There seems to be no attached device")
    }
  }

  override def resetDevice()
      : Unit = {} // we use a physical iPhone, there is no reset

  override def clearStuckModals(): Unit = {
    val cmd =
      s"sshpass -p ${conf.ios.rootpwd} ssh root@${conf.ios.ip} 'activator send libactivator.system.clear-switcher; activator send libactivator.system.homebutton'"
    cmd.!
  }

  override def installApp(app: MobileApp): Unit = {
    // should have last line 'Install: Complete'
    try {
      info(s"installing $app")
      val cmd = s"${conf.ios.ideviceinstaller} --install ${app.path}"
      val ret = cmd.!!
      if (ret.trim.split("\n").last != "Install: Complete")
        throw new RuntimeException(s"install of ${app.path} was not complete")
    } catch {
      case x: RuntimeException => throw UnableToInstallApp(app, x.getMessage)
    }
  }

  override def uninstallApp(appId: String): Unit = {
    // should have last line 'Uninstall: Complete'
    try {
      info(s"uninstall $appId")
      val cmd = s"${conf.ios.ideviceinstaller} --uninstall $appId"
      val ret = cmd.!!
      if (ret.trim.split("\n").last != "Uninstall: Complete")
        throw new RuntimeException(s"uninstall of $appId was not complete")
    } catch {
      case x: RuntimeException =>
        throw UnableToUninstallApp(
          MobileApp(appId, "NA", IOS, "NA"),
          x.getMessage
        )
    }
  }

  override def startApp(appId: String, retries: Int): Unit = {
    val cmd =
      s"sshpass -p ${conf.ios.rootpwd} ssh root@${conf.ios.ip} open $appId"
    cmd.!
    Thread.sleep(10000) // we give each app ten seconds to start up
  }

  override def closeApp(appId: String): Unit = {
    val pid = getPid(appId)
    val cmd =
      s"sshpass -p ${conf.ios.rootpwd} ssh root@${conf.ios.ip} kill -9 $pid"
    cmd.!!
  }

  override def performTouch(x: Int, y: Int): Unit = {
    // there is no touch functionality on iphone - maybe using appium?
  }

  override def setAppPermissions(appId: String): Unit = {
    def setPermission(permission: String, give: Boolean): Int = {
      val value = if (give) 2 else 0
      val timestamp = Instant.now.getEpochSecond
      val run = Process(
        List(
          "sshpass",
          "-p",
          s"${conf.ios.rootpwd}",
          "ssh",
          s"root@${conf.ios.ip}",
          "sqlite3",
          DB_LOCATION,
          s"\"INSERT OR REPLACE INTO access VALUES ('$permission', '$appId', 0, $value, 2, 1, NULL, NULL, 0, 'UNUSED', NULL, 0, $timestamp);\""
        )
      ).run()
      run.exitValue()
    }

    def grantLocationPermission(appId: String): Unit = {
      val openSettings =
        s"sshpass -p ${conf.ios.rootpwd} ssh root@${conf.ios.ip} open com.apple.Preferences"
      info("opening com.apple.Preferences")
      openSettings.!
      Thread.sleep(5000) // sleep to ensure that the settings are actually open
      val cmd = s"node ./resources/frida/iosGrantLocationPermission.js $appId"
      cmd.!
    }

    PERMISSIONS_NOT_GIVE.foreach(perm => setPermission(perm, give = false))
    PERMISSIONS_GIVE.foreach(perm => setPermission(perm, give = true))
    grantLocationPermission(appId)
  }

  override def getForegroundAppId: Option[String] = {
    val cmd = "node ./resources/frida/iosGetForemostAppId.js"
    val ret = cmd.!!.trim.split("\n").head
    if (ret == "null") {
      None
    } else {
      Some(ret)
    }
  }

  override def getPrefs(appId: String): Option[String] = {
    val pid = getPid(appId)
    Some(
      this.runFridaScript(pid, FridaScripts.platform(this.PLATFORM_OS).getPrefs)
    )
  }

  override def getPlatformSpecificData(appId: String): Option[String] = {
    val pid = getPid(appId)
    Some(this.runFridaScript(pid, iOSFridaScripts.getIdfv))
  }

  override def getAppVersion(path: String): Option[String] =
    throw new NotImplementedError()

  override def getPid(appId: String): String = {
    val cmd = s"${conf.ios.fridaps} --usb --applications --json"
    JsonParser(cmd.!!).asInstanceOf[JsArray].elements.find {
      case elem: JsObject =>
        elem.fields("identifier").asInstanceOf[JsString].value == appId
      case _ => false
    } match {
      case Some(value) =>
        value.asJsObject.fields("pid").asInstanceOf[JsNumber].value.toString()
      case None => throw AppClosedItself(appId)
    }
  }

  override def getInstalledApps: Set[String] = { Set() }

  override def checkBootState(): Boolean =
    true // there is no reboot, thus this is always true
}
