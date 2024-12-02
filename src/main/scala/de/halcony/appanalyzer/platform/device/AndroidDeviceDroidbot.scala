package de.halcony.appanalyzer.platform.device

import de.halcony.appanalyzer.Config
import de.halcony.appanalyzer.appbinary.MobileApp
import de.halcony.appanalyzer.platform.PlatformOperatingSystems.{ANDROID, PlatformOS}
import wvlet.log.LogSupport

import scala.sys.process._

class AndroidDeviceDroidbot(config: Config)
    extends AndroidDevice(config)
    with LogSupport {

  override val PLATFORM_OS: PlatformOS = ANDROID
  override val EMULATOR: Boolean = true
  override val ROOT: Boolean = true

  private var emulatorProcess: Option[Process] = None

  private var dead = false

  private def startEmulator(): Process = {
    if (dead) new Error("why u not dead?").printStackTrace()
    val conf = config.emulator.get
    val proxyConf: List[String] = (conf.proxyIP, conf.proxyPort) match {
      case (Some(ip), Some(port)) => List[String]("-http-proxy", s"$ip:$port")
      case _                      => List[String]()
    }
    val snapshotConf: List[String] = conf.snapshot match {
      case Some(value) => List("-snapshot", value)
      case None        => List()
    }
    val cmdArgs: Seq[String] =
      List[String]("-avd", conf.avd) ++ snapshotConf ++ proxyConf
    info(s"starting emulator with args : ${cmdArgs.mkString(" ")}")
    Process(
      conf.emulator,
      cmdArgs
    ).run(
      ProcessLogger(_ => (), _ => ())
    )
  }

  override def installApp(app: MobileApp): Unit = {
    info(s"installing app is handled by droidbot")
  }

  override def uninstallApp(appId: String): Unit = {
    info(s"uninstalling app is handled by droidbot")
  }

  override def startApp(appId: String, retries: Int): Unit = {
    info(s"starting app is handled by droidbot")
  }

  override def getForegroundAppId: Option[String] = {
    info(s"getting foreground app is handled by droidbot")
    // somehow we have to set the correct app here, or build checks differently
    None
  }

  override def startDevice(): Unit = ensureDevice()

  override def stopDevice(): Unit = {
    dead = true
    info("stopping emulator")
    emulatorProcess match {
      case Some(value) =>
        value.destroy()
        emulatorProcess = None
      case None =>
    }
  }

  override def ensureDevice(): Unit = {
    emulatorProcess match {
      case Some(process) =>
        if (!process.isAlive()) {
          emulatorProcess = None
          ensureDevice()
        }
      case None =>
        emulatorProcess = Some(startEmulator())
        Thread.sleep(20000)
        var counter = 0
        while (!checkBootState()) {
          Thread.sleep(20000)
          counter += 1
          if (counter > 3) {
            throw new Error("cannot start the emulator - this is fatal")
          }
        }
    }
  }

  override def resetDevice(): Unit = {
    restartPhone()
  }

  override def restartPhone(): Boolean = {
    emulatorProcess match {
      case Some(value) =>
        value.destroy()
        info("destroyed emulator")
        Thread.sleep(10000)
        emulatorProcess = None
      case None =>
    }
    ensureDevice()
    true
  }

}