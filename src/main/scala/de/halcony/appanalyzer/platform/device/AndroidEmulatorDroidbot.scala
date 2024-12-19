package de.halcony.appanalyzer.platform.device

import de.halcony.appanalyzer.Config
import de.halcony.appanalyzer.appbinary.MobileApp
import de.halcony.appanalyzer.platform.PlatformOperatingSystems.{ANDROID, PlatformOS}
import wvlet.log.LogSupport

import scala.collection.mutable.ListBuffer
import scala.sys.process._

class AndroidEmulatorDroidbot(config: Config)
    extends AndroidEmulatorRoot(config)
    with LogSupport {

  override val PLATFORM_OS: PlatformOS = ANDROID
  override val EMULATOR: Boolean = true
  override val ROOT: Boolean = true

  private var emulatorProcess: Option[Process] = None

  private var dead = false

  override def installApp(app: MobileApp): Unit = {
    info(s"installing app is handled by droidbot")
  }

  override def uninstallApp(appId: String): Unit = {
    info(s"uninstalling app is handled by droidbot")
  }

  override def startApp(appId: String, retries: Int): Unit = {
    info(s"starting app is handled by droidbot")
    val stdio = ListBuffer[String]()
    val stderr = ListBuffer[String]()
    cleanObjectionProcess()
    val cmd =
      s"${conf.android.objection} --gadget $appId explore --startup-command 'android sslpinning disable'"
    val process = Process(cmd)
    objection = Some(
      process.run(
        ProcessLogger(io => stdio.append(io), err => stderr.append(err))
      )
    )
    Thread.sleep(10000) // we give each app 10 seconds to start
  }

  override def getForegroundAppId: Option[String] = {
    info(s"getting foreground app is handled by droidbot")
    // somehow we have to set the correct app here, or build checks differently
    None
  }

  override def startDevice(): Unit = ensureDevice()

  override def resetDevice(): Unit = {
    restartPhone()
  }

  private def cleanObjectionProcess(): Unit = objection match {
    case Some(value) =>
      if (value.isAlive()) {
        value.destroy()
        objection = None
      }
    case None =>
  }
}