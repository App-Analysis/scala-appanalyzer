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

import java.io.{BufferedWriter, File, FileWriter}
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

  /** set whether app installation should be deactivated
    *
    * @param deactivate
    *   the boolean flag to deactivate app installation if true
    */
  def deactivateInstall(deactivate: Boolean): Unit = {
    deactivate_install = deactivate
  }

  /** write a log message to the Frida log file with the given output type
    *
    * @param outType
    *   the type of output (e.g., "info", "error")
    * @param msg
    *   the message to log
    */
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

  /** start the device
    *
    * This method is a no-op as the smartphone is assumed to be already on.
    */
  override def startDevice()
      : Unit = {} // the smartphone is supposed to be already on

  /** get the ADB configuration string for the device
    *
    * @return
    *   a string containing the ADB device identifier if specified, otherwise an
    *   empty string
    */
  protected def getDeviceConfStringAdb: String = device match {
    case Some(value) => s"-s $value"
    case None        => ""
  }

  /** get the configuration string for the objection tool for the device
    *
    * @return
    *   a string containing the objection device identifier if specified,
    *   otherwise an empty string
    */
  protected def getDeviceConfStringObjection: String = device match {
    case Some(value) => s"-S $value"
    case None        => ""
  }

  /** detect if a Frida server process is currently running on the device
    *
    * @return
    *   an Option containing the process ID of the running Frida server if
    *   found, otherwise None
    */
  private def detectRunningFrida(): Option[String] = {
    val cmd =
      s"${conf.android.adb} $getDeviceConfStringAdb shell 'ps -e | grep frida-server'"
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

  /** retrieve the set of installed app package names on the device
    *
    * @return
    *   a set of strings representing the installed app package names
    */
  override def getInstalledApps: Set[String] = {
    val cmd =
      s"${conf.android.adb} $getDeviceConfStringAdb shell pm list packages -f"
    val listOfApps = cmd.!!.split("\n").toList
    // println(listOfApps)
    listOfApps
      .filter(_.startsWith("package:/data/app/"))
      .map(_.split("=").last)
      .toSet
    /*s"${conf.android.adb} $getDeviceConfStringAdb shell pm list packages -f | grep '/data/app/' | sed -e 's/.*=//'".!!.split(
      "\n"
    ).toSet*/
  }

  /** kill a process on the device by its process ID
    *
    * @param pid
    *   the process ID to kill
    */
  private def killProcess(pid: String): Unit = {
    s"${conf.android.adb} $getDeviceConfStringAdb shell 'su -c kill -9 $pid'".!!
  }

  /** start the Frida server on the device
    *
    * This method starts the Frida server and logs its output. If Frida is
    * already running, it attempts to kill the existing process before starting
    * a new one.
    *
    * @throws FridaDied
    *   if the Frida server fails to start properly
    */
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
          s"${conf.android.adb} $getDeviceConfStringAdb shell 'su -c /data/local/tmp/frida-server'"
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

  /** stop the running Frida server on the device
    *
    * This method stops the Frida server process and ensures it is no longer
    * running.
    */
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

  /** execute a block of code with a running Frida server
    *
    * Starts the Frida server, executes the provided function, and stops the
    * Frida server afterwards.
    *
    * @param func
    *   the block of code to execute while Frida is running
    * @return
    *   the result of the executed function
    */
  override def withRunningFrida[T](func: => T): T = {
    startFrida()
    try {
      func
    } finally {
      stopFrida()
    }
  }

  /** clean up the objection process if it is running
    *
    * If an objection process is active, it is destroyed and reset to None.
    */
  private def cleanObjectionProcess(): Unit = objection match {
    case Some(value) =>
      if (value.isAlive()) {
        value.destroy()
        objection = None
      }
    case None =>
  }

  /** get the analysis tool for app package analysis
    *
    * Returns an existing Analysis instance if available, or creates a new APK
    * analysis instance.
    *
    * @param conf
    *   the configuration settings
    * @return
    *   an Analysis instance for app package analysis
    */
  override def getAppPackageAnalysis(conf: Config): Analysis =
    packageAnalysis match {
      case Some(value) => value
      case None =>
        packageAnalysis = Some(APK(conf))
        packageAnalysis.get
    }

  /** ensure that a device is reachable via ADB
    *
    * Checks if the device is accessible and reachable; if not, throws a
    * FatalError.
    */
  override def ensureDevice(): Unit = {
    if (initiallyInstalledApps.isEmpty) {
      initiallyInstalledApps = Some(getInstalledApps)
    }
    val ret =
      s"${conf.android.adb} $getDeviceConfStringAdb get-state" ! ProcessLogger(
        _ => ()
      )
    if (ret != 0)
      throw new FatalError("there is no device reachable via adb")
  }

  /** restart the host ADB server
    *
    * Restarts the ADB server on the host machine to recover from connectivity
    * issues
    */
  protected def restartAdb(): Unit = {
    warn("restarting host adb server")
    s"${conf.android.adb} $getDeviceConfStringAdb kill-server".!!
    s"${conf.android.adb} $getDeviceConfStringAdb start-server".!!
  }

  /** reset the device state
    */
  override def resetDevice(): Unit = {
    cleanObjectionProcess()
  }

  /** simulate a touch input on the device at the specified coordinates
    *
    * @param x
    *   the x-coordinate for the touch
    * @param y
    *   the y-coordinate for the touch
    */
  override def performTouch(x: Int, y: Int): Unit = {
    s"${conf.android.adb} $getDeviceConfStringAdb shell input tap $x $y".!
  }

  /** check whether the device has completed booting
    *
    * @return
    *   true if the device is booted (sys.boot_completed is "1"), false
    *   otherwise
    */
  override def checkBootState(): Boolean = {
    try {
      s"${conf.android.adb} $getDeviceConfStringAdb shell 'getprop sys.boot_completed'".!!.trim == "1"
    } catch {
      case _: Throwable => false
    }
  }

  // todo: I have seen (once) that the screen goes dark on reboot unsure if this could become a permanent issue
  /** restart the phone and ensure it boots up correctly
    *
    * Reboots the device, waits for it to boot, unlocks it, and optionally
    * restarts Frida if it was running
    *
    * @return
    *   true if the phone restarted successfully
    * @throws FatalError
    *   if the device fails to boot or if a fatal error occurs during restart
    */
  override def restartPhone(): Boolean = {
    info("performing phone restart")
    val fridaWasRunning = runningFrida.nonEmpty
    if (runningFrida.nonEmpty)
      stopFrida()
    s"${conf.android.adb} $getDeviceConfStringAdb reboot".!
    var counter = 1
    while (!this.checkBootState() && counter < 10) { // a more dynamic reboot procedure
      Thread.sleep(30000)
      counter = counter + 1
      if (
        counter % 4 == 0
      ) // if after 2 minutes the phone did not appear we should restart adb locally
        restartAdb()
    }
    if (
      !this.checkBootState()
    ) // if we are still not booted something horrible happened which is also fatal
      throw new FatalError(
        "we were unable to successfully restart the phone ... sucks mate"
      )
    s"${conf.android.adb} $getDeviceConfStringAdb shell input keyevent 82".! // this unlocks the phone
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
      if (!this.checkBootState()) {
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

  /** clear any stuck modal dialogs on the device
    */
  override def clearStuckModals(): Unit = {}

  /** install an app on the device
    *
    * Uses ADB to install the app and grants required permissions
    *
    * @param app
    *   the MobileApp instance representing the app to install
    * @throws UnableToInstallApp
    *   if the installation fails
    */
  override def installApp(app: MobileApp): Unit = {
    info(s"installing on device ${app.id} located at ${app.path}")
    val stdio: ListBuffer[String] = ListBuffer()
    val stderr: ListBuffer[String] = ListBuffer()
    val fixedPath = app.path.replace("\"", "")
    val files = new File(fixedPath)
      .listFiles()
      .filter(_.getName.endsWith(".apk"))
      .map(_.getAbsolutePath)
    val cmd =
      s"${conf.android.adb} $getDeviceConfStringAdb install-multiple -g ${files.mkString(" ")}"
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

  /** uninstall an app from the device
    *
    * Attempts to uninstall the app using ADB with multiple retries, handling
    * various error codes.
    *
    * @param appId
    *   the package name of the app to uninstall
    * @throws UnableToUninstallApp
    *   if the app cannot be uninstalled after retries
    */
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
      val cmd = s"${conf.android.adb} $getDeviceConfStringAdb uninstall $appId"
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

  /** start an app on the device using objection
    *
    * @param appId
    *   the package name of the app to start
    * @param noStartCheck
    *   if true, skips the verification of the app being in the foreground
    * @param retries
    *   the maximum number of startup attempts
    * @throws UnableToStartApp
    *   if the app fails to start after the specified retries
    */
  override def startApp(
      appId: String,
      noStartCheck: Boolean,
      retries: Int = 3
  ): Unit = {
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
        s"${conf.android.objection} $getDeviceConfStringObjection --gadget $appId explore --startup-command 'android sslpinning disable'"
      info(cmd)
      val process = Process(cmd)
      objection = Some(
        process.run(
          ProcessLogger(io => stdio.append(io), err => stderr.append(err))
        )
      )
      Thread.sleep(10000) // we give each app 10 seconds to start
      if (objection.get.isAlive()) {
        val fgid = getForegroundAppId.getOrElse(throw AppClosedItself(appId))
        // chrome indicates a TWA or CT startup
        if ((fgid != appId && fgid != "com.android.chrome") && !noStartCheck) {
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

  /** close any running browser (Chrome) on the device
    *
    * Attempts to force-stop the Chrome browser using ADB.
    */
  override def closePossibleBrowser(): Unit = {
    val browserAppId = "com.android.chrome"
    val cmd =
      s"${conf.android.adb} $getDeviceConfStringAdb shell am force-stop $browserAppId"
    if (conf.verbose) cmd.! else cmd ! ProcessLogger(_ => ())
  }

  /** close the specified app on the device
    *
    * Closes any possible browser and then force-stops the app using ADB.
    *
    * @param appId
    *   the package name of the app to close
    */
  override def closeApp(appId: String): Unit = {
    closePossibleBrowser()
    cleanObjectionProcess()
    info(s"closing app $appId")
    val cmd =
      s"${conf.android.adb} $getDeviceConfStringAdb shell am force-stop $appId"
    if (conf.verbose) cmd.! else cmd ! ProcessLogger(_ => ())
  }

  /** set all required permissions for the specified app
    *
    * @param appId
    *   the package name of the app for which to set permissions
    */
  override def setAppPermissions(appId: String): Unit = {
    info(s"setting permissions for $appId")
    val cmd =
      s"${conf.android.adb} $getDeviceConfStringAdb shell pm list permissions -g -d -u"
    val permissions = cmd.!!
    permissions
      .split("\n")
      .filter(_.startsWith("  permission:"))
      .map(_.replace("  permission:", ""))
      .foreach { permission =>
        try {
          s"${conf.android.adb} $getDeviceConfStringAdb shell pm grant $appId $permission" ! ProcessLogger(
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

  /** get the package name of the app currently in the foreground
    *
    * @return
    *   an Option containing the package name of the foreground app, or None if
    *   not found
    */
  override def getForegroundAppId: Option[String] = {
    val cmd =
      s"${conf.android.adb} $getDeviceConfStringAdb shell dumpsys activity" // | grep -E 'mCurrentFocus' | cut -d '/' -f1 | sed 's/.* //g'"
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

  /** get the process ID (PID) of the specified app
    *
    * @param appid
    *   the package name of the app
    * @return
    *   the process ID as a String
    * @throws AppClosedItself
    *   if the app is no longer running
    */
  override def getPid(appid: String): String = {
    try {
      val cmd =
        s"${conf.android.adb} $getDeviceConfStringAdb shell pidof -s $appid"
      cmd.!!.trim
    } catch {
      case _: RuntimeException => // if we have a runtime exception it is an exit value -1
        // which indicates that the app ain't running no more
        throw AppClosedItself(appid)
    }
  }

  /** retrieve preferences or configuration data for the specified app using
    * Frida
    *
    * @param appId
    *   the package name of the app
    * @return
    *   an Option containing the retrieved preferences as a String
    */
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

  /** get platform-specific data for the specified app
    *
    * @param appId
    *   the package name of the app
    * @return
    *   None
    */
  override def getPlatformSpecificData(appId: String): Option[String] =
    None

  /** retrieve the app version from the app at the specified path
    *
    * @param path
    *   the path to the app
    * @return
    *   throws NotImplementedError
    */
  override def getAppVersion(path: String): Option[String] =
    throw new NotImplementedError()

}
