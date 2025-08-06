package de.halcony.appanalyzer.platform.device
import de.halcony.appanalyzer.Config
import de.halcony.appanalyzer.platform.PlatformOperatingSystems.{
  ANDROID,
  PlatformOS
}
import de.halcony.appanalyzer.platform.exceptions.FatalError

import scala.sys.process._

class AndroidDeviceNonRoot(conf: Config, device: Option[String])
    extends AndroidDevice(conf, device) {

  override val PLATFORM_OS: PlatformOS = ANDROID
  override val EMULATOR: Boolean = false
  override val ROOT: Boolean = false

  override def checkBootState(): Boolean =
    try {
      s"${conf.android.adb} $getDeviceConfStringAdb shell 'getprop sys.boot_completed'".!!.trim == "1"
    } catch {
      case _: Throwable => false
    }

  override def startFrida(): Unit = {} // no root -> no frida

  override def stopFrida(): Unit = {} // no root -> no frida

  override def withRunningFrida[T](func: => T): T = func // no root -> no frida

  override def restartPhone(): Boolean = {
    s"${conf.android.adb} $getDeviceConfStringAdb reboot".!
    var counter = 1
    while (!checkBootState() && counter < 10) {
      Thread.sleep(30000)
      counter = counter + 1
      if (counter % 4 == 1) restartAdb()
    }
    if (!checkBootState())
      throw new FatalError(
        "We were unable to successfully restart the phone ... sucks mate"
      )
    Thread.sleep(1000)
    performTouch(1000, 800) // todo: this should be configured in the config
    info(
      "we unlocked the phone, now we wait for another 2 minutes for everything to boot up"
    )
    Thread.sleep(120000)
    if (checkBootState()) {
      true
    } else {
      throw new FatalError(
        "After unlocking the phone died ... sucks even more mate"
      )
    }
  }

  /*private def restartAdb() : Unit = {
    warn("restarting host adb server")
    s"${conf.android.adb} kill-server".!!
    s"${conf.android.adb} start-server".!!
  }*/

  /*override def getAppPackageAnalysis(conf: Config): Analysis =
    packageAnalysis match {
      case Some(value) => value
      case None =>
        packageAnalysis = Some(APK(conf))
        packageAnalysis.get
    }*/

  /*override def ensureDevice(): Unit = {
    val ret = s"${conf.android.adb} get-state" ! ProcessLogger(_ => ())
    if (ret != 0)
      throw new FatalError("there is no device reachable via adb")
  }*/

  override def resetDevice()
      : Unit = {} // no frida/objection means no process to reset

  /*override def clearStuckModals(): Unit = */

  /*override def installApp(app: MobileApp): Unit = ???*/

  /*override def uninstallApp(appId: String): Unit = ???*/

  override def startApp(appId: String, noAppStartCheck : Boolean, retries: Int): Unit = {
    val _ =
      s"${conf.android.adb} $getDeviceConfStringAdb shell monkey -p $appId 1".!!
    Thread.sleep(10000)
    // info(ret)
  }

  /*override def closeApp(appId: String): Unit = ???*/

  /*override def performTouch(x: Int, y: Int): Unit = ???*/

  /*override def setAppPermissions(appId: String): Unit = ???*/

  /*override def getForegroundAppId: Option[String] = {

  }*/

  override def getPrefs(appId: String): Option[String] =
    None // no frida no prefs

  override def getPlatformSpecificData(appId: String): Option[String] = None

  override def getAppVersion(path: String): Option[String] =
    throw new NotImplementedError()

  /*override def getPid(appId: String): String = ???*/
}
