package de.halcony.appanalyzer.platform.device
import de.halcony.appanalyzer.Config
import de.halcony.appanalyzer.platform.PlatformOperatingSystems.{
  ANDROID,
  PlatformOS
}
import de.halcony.appanalyzer.platform.exceptions.FatalError

import scala.sys.process._

class AndroidDeviceDroidbot(conf: Config, device: Option[String])
    extends AndroidDevice(conf, device) {

  override val PLATFORM_OS: PlatformOS = ANDROID
  override val EMULATOR: Boolean = false
  override val ROOT: Boolean = false

  /** check whether the device has completed booting
    *
    * @return
    *   true if the device has booted successfully, false otherwise
    */
  override def checkBootState(): Boolean = {
    try {
      s"${conf.android.adb} $getDeviceConfStringAdb shell 'getprop sys.boot_completed'".!!.trim == "1"
    } catch {
      case _: Throwable => false
    }
  }

  /** Start frida, but no root
    */
  override def startFrida(): Unit = {} // no root -> no frida

  /** stop Frida, but no root
    */
  override def stopFrida(): Unit = {} // no root -> no frida

  /** no root
    */
  override def withRunningFrida[T](func: => T): T = func // no root -> no frida

  /** restart the phone.
    *
    * Reboots the device using ADB, waits for it to boot, and performs an unlock
    * action
    *
    * @return
    *   true if the device restarted and booted successfully, otherwise throws a
    *   FatalError
    */
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

  /** reset the device state.
    *
    * For Droidbot, no additional reset operations are required since Frida and
    * objection are not used
    */
  override def resetDevice()
      : Unit = {} // no frida/objection means no process to reset

  /*override def clearStuckModals(): Unit = */

  /*override def installApp(app: MobileApp): Unit = ???*/

  /** uninstall an app
    *
    * Logs that Droidbot has already uninstalled the app
    *
    * @param appId
    *   the package name of the app to uninstall
    */
  override def uninstallApp(appId: String): Unit = {
    info("Droidbot has already uninstalled app.")
  }

  /** start an app
    *
    * Logs that Droidbot will start the app
    *
    * @param appId
    *   the package name of the app to start
    * @param noAppStartCheck
    *   if true, skips checking whether the app has started successfully
    * @param retries
    *   the number of retry attempts allowed
    */
  override def startApp(
      appId: String,
      noAppStartCheck: Boolean,
      retries: Int
  ): Unit = {
    info("Droidbot will start app.")
  }

  /*override def closeApp(appId: String): Unit = ???*/

  /*override def performTouch(x: Int, y: Int): Unit = ???*/

  /*override def setAppPermissions(appId: String): Unit = ???*/

  /*override def getForegroundAppId: Option[String] = {

  }*/

  /** retrieve app preferences
    *
    * For Droidbot, returns None since Frida is not supported
    *
    * @param appId
    *   the package name of the app
    * @return
    *   None
    */
  override def getPrefs(appId: String): Option[String] =
    None // no frida no prefs

  /** retrieve platform-specific data
    *
    * For Droidbot, no additional platform-specific data is provided
    *
    * @param appId
    *   the package name of the app
    * @return
    *   None
    */
  override def getPlatformSpecificData(appId: String): Option[String] = None

  /** retrieve the app version
    *
    * Not implemented for Droidbot
    *
    * @param path
    *   the path to the app binary
    * @return
    *   always throws NotImplementedError
    */
  override def getAppVersion(path: String): Option[String] =
    throw new NotImplementedError()

  /*override def getPid(appId: String): String = ???*/
}
