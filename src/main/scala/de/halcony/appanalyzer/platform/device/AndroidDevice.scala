package de.halcony.appanalyzer.platform.device

import de.halcony.appanalyzer.Config
import de.halcony.appanalyzer.appbinary.apk.APK
import de.halcony.appanalyzer.appbinary.{Analysis, MobileApp}
import de.halcony.appanalyzer.platform.frida.FridaScripts
import de.halcony.appanalyzer.platform.PlatformOperatingSystems.{
  ANDROID,
  PlatformOS
}
import de.halcony.appanalyzer.platform.exceptions.{
  AppClosedItself,
  FatalError,
  FridaDied,
  UnableToInstallApp,
  UnableToStartApp,
  UnableToUninstallApp
}
import wvlet.log.LogSupport

import java.io.{BufferedWriter, FileWriter}
import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{Duration, MILLISECONDS}
import scala.concurrent.{Await, Future}
import scala.sys.process.{Process, ProcessLogger, _}

case class AndroidDevice(conf: Config, device: Option[String])
    extends Device
    with LogSupport {

  private val FRIDA_LOG_FILE = "./frida.log"
  private var deactivate_install = false

  def deactivateInstall(deactivate: Boolean): Unit = {
    deactivate_install = deactivate
  }

  private def writeFridaLog(outType: String, msg: String): Unit = synchronized {
    val writer = new BufferedWriter(new FileWriter(FRIDA_LOG_FILE, true))
    try {

      writer.write(
        s"[${java.time.LocalDateTime.now().toString}][$outType] $msg\n"
      )
    } finally {
      writer.flush()
      writer.close()
    }
  }

  /** adb return codes
    *
    * 1 generic failure 20 means that a service has not been found (i.e.,
    * settings, package) - restart is required
    */
  override val PLATFORM_OS: PlatformOS = ANDROID
  override val EMULATOR: Boolean = false
  override val ROOT: Boolean = true

  var objection: Option[Process] = None
  private var packageAnalysis: Option[Analysis] = None

  private var runningFrida: Option[Process] = None

  override def startDevice()
      : Unit = {} // the smartphone is supposed to be already on

  protected def getDeviceConfString: String = device match {
    case Some(value) => s"--serial $value"
    case None        => ""
  }

  private def detectRunningFrida(): Option[String] = {
    val cmd =
      s"${conf.android.adb} $getDeviceConfString shell 'ps -e | grep frida-server'"
    try {
      cmd.!!.replace("\t", " ")
        .split(" ")
        .map(_.trim)
        .filter(_ != "")
        .toList match {
        case Nil =>
          None // this is good it means there is no frida-server running
        case some =>
          // this is not as good it means there is a frida-server running
          assert(
            some.length == 9,
            s"the encountered ps -e line is out of spec ${some.mkString("#")}"
          )
          Some(some.tail.head)
      }
    } catch {
      // if the cmd returns non zero it means grep did not find anything
      case x: RuntimeException
          if x.getMessage.toLowerCase.contains("nonzero exit value") =>
        None
    }
  }

  override def getInstalledApps: Set[String] = {
    s"${conf.android.adb} $getDeviceConfString shell pm list packages -f | grep '/data/app/' | sed -e 's/.*=//'".!!.split(
      "\n"
    ).toSet
  }

  private def killProcess(pid: String): Unit = {
    s"${conf.android.adb} $getDeviceConfString shell 'su -c kill -9 $pid'".!!
  }

  override def startFrida(): Unit = {
    info("starting frida")
    if (runningFrida.nonEmpty) {
      warn("Frida already running, cannot be started twice")
    } else {
      detectRunningFrida() match {
        case Some(pid) =>
          warn(
            s"we encountered a still running frida instance as pid $pid, lets get killin'"
          )
          killProcess(pid)
        case None =>
      }
      val process =
        Process(
          s"${conf.android.adb} $getDeviceConfString shell 'su -c /data/local/tmp/frida-server'"
        )
          .run(
            ProcessLogger(
              in => writeFridaLog("info", in),
              err => writeFridaLog("error", err)
            )
          )
      runningFrida = Some(process)
      Thread.sleep(
        2000
      ) // this ensures that if frida dies on startup we can see/catch it
      if (!process.isAlive()) {
        increaseFailedInteractions()
        runningFrida = None
        throw FridaDied()
      }
    }
  }

  override def stopFrida(): Unit = {
    info("stopping frida")
    runningFrida match {
      case Some(process) =>
        if (!process.isAlive()) {
          increaseFailedInteractions()
          // throw FridaDied()
        } else {
          process.destroy()
        }
        runningFrida = None
        detectRunningFrida() match {
          case Some(pid) =>
            // apparently it is to be expected that destroying the process won't kill the cmd
            killProcess(pid)
          case None =>
        }
      case None => warn("trying to destroy a non existing frida instance")
    }
  }

  override def withRunningFrida[T](func: => T): T = {
    startFrida()
    try {
      func
    } finally {
      stopFrida()
    }
  }

  private def cleanObjectionProcess(): Unit = objection match {
    case Some(value) =>
      if (value.isAlive()) {
        value.destroy()
        objection = None
      }
    case None =>
  }

  override def getAppPackageAnalysis(conf: Config): Analysis =
    packageAnalysis match {
      case Some(value) => value
      case None =>
        packageAnalysis = Some(APK(conf))
        packageAnalysis.get
    }

  override def ensureDevice(): Unit = {
    if (initiallyInstalledApps.isEmpty) {
      initiallyInstalledApps = Some(getInstalledApps)
    }
    val ret =
      s"${conf.android.adb} $getDeviceConfString get-state" ! ProcessLogger(_ =>
        ()
      )
    if (ret != 0)
      throw new FatalError("there is no device reachable via adb")
  }

  protected def restartAdb(): Unit = {
    warn("restarting host adb server")
    s"${conf.android.adb} $getDeviceConfString kill-server".!!
    s"${conf.android.adb} $getDeviceConfString start-server".!!
  }

  override def resetDevice(): Unit = {
    cleanObjectionProcess()
  }

  override def performTouch(x: Int, y: Int): Unit = {
    s"${conf.android.adb} $getDeviceConfString shell input tap $x $y".!
  }

  override def checkBootState(): Boolean = {
    try {
      s"${conf.android.adb} $getDeviceConfString shell 'getprop sys.boot_completed'".!!.trim == "1"
    } catch {
      case _: Throwable => false
    }
  }

  // todo: I have seen (once) that the screen goes dark on reboot unsure if this could become a permanent issue
  override def restartPhone(): Boolean = {
    info("performing phone restart")
    val fridaWasRunning = runningFrida.nonEmpty
    if (runningFrida.nonEmpty)
      stopFrida()
    s"${conf.android.adb} $getDeviceConfString reboot".!
    var counter = 1
    while (!checkBootState() && counter < 10) { // a more dynamic reboot procedure
      Thread.sleep(30000)
      counter = counter + 1
      if (
        counter % 4 == 0
      ) // if after 2 minutes the phone did not appear we should restart adb locally
        restartAdb()
    }
    if (
      !checkBootState()
    ) // if we are still not booted something horrible happened which is also fatal
      throw new FatalError(
        "we were unable to successfully restart the phone ... sucks mate"
      )
    s"${conf.android.adb} $getDeviceConfString shell input keyevent 82".! // this unlocks the phone
    Thread.sleep(1000)
    performTouch(
      1000,
      800
    ) // those are magic numbers working for Galaxy A13 to remove the notification of storage access
    info(
      "we unlocked the phone, now we wait for another 2 minutes for everything to boot up"
    )
    Thread.sleep(120000)
    if (fridaWasRunning)
      startFrida()
    Thread.sleep(1000)
    try {
      if (!checkBootState()) {
        throw new FatalError("getprop sys.boot_completed was not 1")
      }
    } catch {
      case x: Throwable =>
        increaseFailedInteractions()
        throw new FatalError(
          s"restarting the phone resulted in ${x.getMessage}"
        )
    }
    ensureDevice()
    true
  }

  override def clearStuckModals(): Unit = {}

  override def installApp(app: MobileApp): Unit = {
    info(s"installing on device ${app.path}")
    val stdio: ListBuffer[String] = ListBuffer()
    val stderr: ListBuffer[String] = ListBuffer()
    val cmd =
      s"${conf.android.adb} $getDeviceConfString install-multiple -g ${app.path}"
    val ret =
      if (conf.verbose) cmd.!
      else
        cmd ! ProcessLogger(io => stdio.append(io), err => stderr.append(err))
    if (ret != 0) {
      increaseFailedInteractions()
      throw UnableToInstallApp(
        app,
        s"ret value of install was $ret\nSTDIO\n${stdio
            .mkString("\n")}\nSTDERR\n${stderr.mkString("\n")}"
      )
    }
  }

  override def uninstallApp(appId: String): Unit = {
    cleanObjectionProcess()
    val UNINSTALL_TIMEOUT_MS: Long = 30000 // 30 seconds
    val MAX_TRIES = 3
    closeApp(appId)
    var success = false
    var tries = 0
    var ret = -42
    var stdio: ListBuffer[String] = ListBuffer()
    var stderr: ListBuffer[String] = ListBuffer()
    while (tries < MAX_TRIES && !success) {
      info(s"uninstalling app $appId")
      tries = tries + 1
      val cmd = s"${conf.android.adb} $getDeviceConfString uninstall $appId"
      var uninstallProcess: Process = null
      stdio = ListBuffer()
      stderr = ListBuffer()
      val uninstallFuture: Future[Int] = Future {
        val proc = Process(cmd)
        uninstallProcess =
          proc.run(ProcessLogger(stdio append _, stderr append _))
        uninstallProcess.exitValue()
      }
      try {
        ret = Await.result(
          uninstallFuture,
          Duration(UNINSTALL_TIMEOUT_MS, MILLISECONDS)
        )
        if (ret == 0) {
          success = true
        } else if (ret == 20 || ret == 224) {
          warn(
            "we encountered ret code 20 or 224 which indicates that a phone restart is required"
          )
          increaseFailedInteractions()
          restartPhone()
        } else {
          warn(s"adb uninstall returned $ret retrying \nSTDIO\n${stdio
              .mkString("\n")}\nSTDERR\n${stderr.mkString("\n")}")
          increaseFailedInteractions()
          success = false

          info(s"APP: $appId, LIST ${getInstalledApps}")

          if (!getInstalledApps.contains(appId)) {
            warn(s"the app $appId does not seem to be installed")
            success = true
            ret = 0
          }
        }
      } catch {
        case x: Throwable =>
          if (uninstallProcess.isAlive()) uninstallProcess.destroy()
          throw UnableToUninstallApp(
            MobileApp(appId, "NA", ANDROID, "NA"),
            s"adb returned $ret\n${x.getMessage}\nSTDIO\n${stdio
                .mkString("\n")}\nSTDERR\n${stderr.mkString("\n")}"
          )
      }
    }
    ret match {
      case 0 =>
      case x =>
        error(s"final adb uninstall try resulted in ret code $x")
        throw UnableToUninstallApp(
          MobileApp(appId, "NA", ANDROID, "NA"),
          s"adb returned $ret\nSTDIO\n${stdio.mkString("\n")}\nSTDERR\n${stderr.mkString("\n")}"
        )
    }
  }

  override def startApp(appId: String, retries: Int = 3): Unit = {
    var counter = 0
    var done = false
    var stdio = ListBuffer[String]()
    var stderr = ListBuffer[String]()
    while (counter < retries && !done) {
      stdio = ListBuffer[String]()
      stderr = ListBuffer[String]()
      info(s"starting app $appId")
      cleanObjectionProcess()
      val cmd =
        s"${conf.android.objection} $getDeviceConfString --gadget $appId explore --startup-command 'android sslpinning disable'"
      val process = Process(cmd)
      objection = Some(
        process.run(
          ProcessLogger(io => stdio.append(io), err => stderr.append(err))
        )
      )
      Thread.sleep(10000) // we give each app 10 seconds to start
      if (objection.get.isAlive()) {
        val fgid = getForegroundAppId.getOrElse(throw AppClosedItself(appId))
        if (fgid != appId) {
          warn(s"foreground id is wrong : '$fgid' instead of '$appId'")
          closeApp(appId)
        } else {
          done = true
        }
      } else {
        warn("objection died after starting retrying...")
        increaseFailedInteractions()
        closeApp(appId)
      }
      counter = counter + 1
    }
    if (objection.nonEmpty && !objection.get.isAlive()) {
      objection = None
      info("finally unable to start app, throwing corresponding error")
      // we do not need to increase failed interactions as this has already been done in the loop
      throw UnableToStartApp(
        appId,
        s"STDIO\n${stdio.mkString("\n")}\nSTDERR\n${stderr.mkString("\n")}"
      )
    } else {
      // only successfully starting an app counts as a failure reset
      resetFailedInteractions()
    }
  }

  override def closeApp(appId: String): Unit = {
    cleanObjectionProcess()
    info(s"closing app $appId")
    val cmd =
      s"${conf.android.adb} $getDeviceConfString shell am force-stop $appId"
    if (conf.verbose) cmd.! else cmd ! ProcessLogger(_ => ())
  }

  override def setAppPermissions(appId: String): Unit = {
    info(s"setting permissions for $appId")
    val cmd =
      s"${conf.android.adb} $getDeviceConfString shell pm list permissions -g -d -u"
    val permissions = cmd.!!
    permissions
      .split("\n")
      .filter(_.startsWith("  permission:"))
      .map(_.replace("  permission:", ""))
      .foreach { permission =>
        try {
          s"${conf.android.adb} $getDeviceConfString shell pm grant $appId $permission" ! ProcessLogger(
            _ => ()
          )
        } catch {
          case _: Throwable =>
        }
      }
  }

  /*override def getForegroundAppId: Option[String] = {
    val cmd = s"${conf.android.adb} shell dumpsys activity recents"
    cmd.!!.split("\n").find(_.contains("Recent #0")) match {
      case Some(value) =>
        "A=[0-9]+:(.+?) U=".r.findFirstIn(value) match {
          case Some(value) =>
            Some(
              value.substring("A=[0-9]+:".r.findFirstIn(value).get.length,
                              value.length - " U=".length))
          case None => None
        }
      case None => None
    }
  }*/

  override def getForegroundAppId: Option[String] = {
    val cmd =
      s"${conf.android.adb} $getDeviceConfString shell dumpsys activity" // | grep -E 'mCurrentFocus' | cut -d '/' -f1 | sed 's/.* //g'"
    val ret = cmd.!!
    ret.split("\n").find(_.contains("mCurrentFocus=")) match {
      case Some(value) =>
        try {
          val _ :: rhs :: Nil = value.split("=").toList
          val _ :: _ :: idAndAction :: Nil = rhs.split(" ").toList
          Some(idAndAction.split("/").head)
        } catch {
          case _: MatchError =>
            error(s"cannot process $value")
            error(ret)
            None
        }
      case None =>
        error("no current focus found")
        None
    }
  }

  override def getPid(appid: String): String = {
    try {
      val cmd =
        s"${conf.android.adb} $getDeviceConfString shell pidof -s $appid"
      cmd.!!.trim
    } catch {
      case _: RuntimeException => // if we have a runtime exception it is an exit value -1
        // which indicates that the app ain't running no more
        throw AppClosedItself(appid)
    }
  }

  override def getPrefs(appId: String): Option[String] = {
    val script = FridaScripts.platform(this.PLATFORM_OS).getPrefs
    val pid = getPid(appId)
    val prefs = runFridaScript(pid, script)
    val insertText = getPlatformSpecificData(appId) match {
      case Some(value) => prefs + "\n#########\n" + value
      case None        => prefs
    }
    Some(insertText)
  }

  override def getPlatformSpecificData(appId: String): Option[String] =
    None

  override def getAppVersion(path: String): Option[String] =
    throw new NotImplementedError()

}
