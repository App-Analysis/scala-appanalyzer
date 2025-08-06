package de.halcony.appanalyzer.analysis

import de.halcony.appanalyzer.analysis.exceptions.{
  AnalysisFatal,
  InterfaceAnalysisCondition,
  MissingInterfaceElement,
  SkipThisApp,
  StaleInterfaceElement,
  UncaughtCondition,
  WebDriverHissyFit
}
import de.halcony.appanalyzer.analysis.interaction.Interface
import de.halcony.appanalyzer.analysis.trafficcollection.TrafficCollection
import de.halcony.appanalyzer.appbinary.MobileApp
import de.halcony.appanalyzer.database.Postgres
import de.halcony.appanalyzer.{Config, Experiment, platform}
import de.halcony.appanalyzer.platform.PlatformOperatingSystems
import de.halcony.appanalyzer.platform.appium.{Appium, NoAppium, iOSAppium}
import de.halcony.appanalyzer.platform.device.Device
import de.halcony.appanalyzer.platform.exceptions.{
  AppClosedItself,
  FatalError,
  FridaDied,
  UnableToInstallApp,
  UnableToStartApp,
  UnableToUninstallApp
}
import Analysis.AnalysisTookTooLong
import de.halcony.appanalyzer.analysis.plugin.ActorPlugin
import org.openqa.selenium.{StaleElementReferenceException, WebDriverException}
import scalikejdbc.scalikejdbcSQLInterpolationImplicitDef
import spray.json.{JsObject, JsString}
import wvlet.log.LogSupport

import java.time.ZonedDateTime
import scala.concurrent.duration.Duration.Inf
import scala.concurrent.duration.{Duration, MILLISECONDS}
import scala.concurrent.{Await, Future, TimeoutException}
import scala.concurrent.ExecutionContext.Implicits.global

/** the Analysis class orchestrates the complete analysis process for a mobile
  * app
  *
  * It manages the lifecycle of an analysis run including the initialization of
  * traffic collection, interaction with the app interface via an actor plugin,
  * error handling, and logging of analysis data to a database. The analysis can
  * repeatedly interact with the app until the actor indicates completion or a
  * fatal error occurs
  *
  * @param description
  *   A description for the analysis run
  * @param app
  *   The mobile app to be analyzed
  * @param actor
  *   The actor plugin that defines the analysis actions
  * @param device
  *   The device on which the analysis is executed
  * @param conf
  *   The configuration settings
  * @param noAppStartCheck
  *   Flag to disable the check for app startup
  */
class Analysis(
    description: String,
    app: MobileApp,
    actor: ActorPlugin,
    device: Device,
    conf: Config,
    noAppStartCheck: Boolean
) extends LogSupport {

  private var id: Option[Int] = None
  private var activeTrafficCollection: Option[TrafficCollection] = None
  private var trafficCollectionStart: Option[ZonedDateTime] = None
  private var stop: Boolean = false
  private var running: Boolean = false
  private var collectInterfaceElements: Boolean = true

  /** sets whether to collect interface elements during the analysis
    *
    * @param value
    *   true to collect interface elements; false otherwise
    */
  def setCollectInterfaceElements(value: Boolean): Unit = {
    collectInterfaceElements = value
  }

  /** set the running state of the analysis
    *
    * @param value
    *   the boolean value to which to set it
    */
  private def setRunning(value: Boolean): Unit = synchronized {
    running = value
  }

  /** checks if the device is rooted
    *
    * @return
    *   true if the device is rooted, false otherwise
    */
  def deviceIsRooted: Boolean = synchronized {
    device.ROOT
  }

  /** retrieves the current running state of the analysis
    *
    * @return
    *   true if the analysis is running, false otherwise
    */
  def getRunning: Boolean = synchronized {
    running
  }

  /** sets the stop flag, indicating that the analysis should terminate
    */
  private def setStop(): Unit = synchronized {
    stop = true
  }

  /** checks whether the analysis has been signaled to stop
    *
    * If the stop flag is set, throws an AnalysisTookTooLong exception
    */
  def checkStop(): Unit = synchronized {
    if (stop) {
      throw new AnalysisTookTooLong()
    }
  }

  /** retrieves the unique identifier for this analysis run
    *
    * @return
    *   the analysis ID
    */
  def getId: Int = id.get

  /** returns the mobile app currently being analyzed
    *
    * @return
    *   the current MobileApp instance
    */
  def getCurrentApp: MobileApp = app

  /** starts a dummy traffic collection
    *
    * This is used for testing purposes
    */
  def startDummyTrafficCollection(): Unit = {
    info("starting dummy traffic collection")
    activeTrafficCollection match {
      case Some(_) =>
        warn("there is already an active traffic collection")
      case None =>
        activeTrafficCollection = Some(
          TrafficCollection.startDumbTrafficCollection(conf)
        )
        trafficCollectionStart = Some(ZonedDateTime.now())
    }
  }

  /** starts a traffic collection associated with the analysis
    *
    * @param related
    *   An Interface related to the traffic collection
    * @param comment
    *   A comment describing the traffic collection
    * @param parameters
    *   Optional additional parameters for the traffic collection
    */
  def startTrafficCollection(
      related: Option[Interface],
      comment: String,
      parameters: Option[Seq[String]] = None
  ): Unit = {
    info("starting traffic collection")
    val interfaceId = related match {
      case Some(value) => Some(value.getId)
      case None        => None
    }
    activeTrafficCollection match {
      case Some(_) =>
        warn("there is an already active traffic collection")
      case None =>
        activeTrafficCollection = Some(
          TrafficCollection
            .startNewTrafficCollection(
              this.getId,
              interfaceId,
              comment,
              conf,
              port = conf.mitm.port,
              parameters
            )
        )
        trafficCollectionStart = Some(ZonedDateTime.now())
    }
  }

  /** retrieves the start time of the current traffic collection
    *
    * @return
    *   the ZonedDateTime when the traffic collection started
    * @throws FatalError
    *   if traffic collection was never started
    */
  def getTrafficCollectionStart: ZonedDateTime =
    trafficCollectionStart.getOrElse(
      throw new FatalError(
        "requesting traffic collection start time without ever starting traffic collection"
      )
    )

  /** stops the active traffic collection
    */
  def stopTrafficCollection(): Unit = {
    info("stopping traffic collection")
    activeTrafficCollection match {
      case Some(_) =>
        TrafficCollection.stopCurrentTrafficCollection()
        activeTrafficCollection = None
        trafficCollectionStart = None
      case None =>
    }
  }

  /** collects and stores the current app's preferences
    *
    * @param comment
    *   A comment describing the preference collection
    * @param context
    *   Optional context identifier
    */
  def collectCurrentAppPreferences(
      comment: String,
      context: Option[Int] = None
  ): Unit = {
    info("collecting app preferences")
    val preferences = JsObject(
      "preferences" -> JsString(device.getPrefs(app.id).getOrElse("")),
      "platformSpecifics" -> JsString(
        device.getPlatformSpecificData(app.id).getOrElse("")
      )
    ).prettyPrint
    Postgres.withDatabaseSession { implicit session =>
      sql"""INSERT INTO AppPreferences (
                            analysis,
                            interface,
                            appid,
                            version,
                            os,
                            comment,
                            prefs
                            )
               VALUES (
                    ${this.getId},
                    ${context.orNull},
                    ${app.id},
                    ${app.version},
                    ${app.getOsString},
                    $comment,
                    $preferences
               )""".update.apply()
    }
  }

  /** checks if the app is still running on the device
    *
    * @param fail
    *   true to throw an exception if the app is not running
    * @return
    *   true if the app is running, false otherwise
    */
  def checkIfAppIsStillRunning(fail: Boolean): Boolean = {
    if (app.id == "EMPTY") {
      true
    } else {
      val running = device.getForegroundAppId match {
        case Some(value) =>
          if (value == app.id) {
            true
          } else {
            if (value == "com.android.chrome") {
              warn(
                "there is chrome in the foreground this is possibly due to CT or TWA!"
              )
              true
            } else {
              error(
                s"the foreground app is $value but we expected ${app.id} - this indicates that the app closed itself"
              )
              false
            }
          }
        case None => false
      }
      if (!running && fail) throw AppClosedItself(app.id)
      running
    }
  }

  /** handles post-app startup tasks to retrieve the initial interface
    *
    * Depending on the platform, interacts with Appium to remove alerts and
    * returns an Interface object representing the app's current state
    *
    * @param interfaceComment
    *   A comment for the interface
    * @param appium
    *   The Appium instance used for interacting with the app
    * @param device
    *   The device on which the app is running
    * @return
    *   an Interface instance representing the current app interface
    */
  private def handlePostAppStartup(
      interfaceComment: String,
      appium: Appium,
      device: Device
  ): Interface = {
    device.PLATFORM_OS match {
      case PlatformOperatingSystems.ANDROID =>
        interaction.Interface(
          this,
          appium,
          flat = !collectInterfaceElements,
          interfaceComment
        ) // nothing to do here
      case platform.PlatformOperatingSystems.IOS =>
        // we have to make sure that we have appium access prior to removing any alerts
        if (
          !appium.isInstanceOf[NoAppium] && appium
            .asInstanceOf[iOSAppium]
            .getRidOfAlerts(conf)
        )
          interaction.Interface(
            this,
            appium,
            flat = !collectInterfaceElements,
            interfaceComment
          )
        else
          interaction.Interface(
            this,
            appium,
            flat = !collectInterfaceElements,
            interfaceComment
          )
    }
  }

  /** performs the main analysis workflow
    *
    * This method sets the analysis as running, inserts a new analysis record
    * into the database, and then uses Appium and the actor plugin to interact
    * with the app until the actor indicates that no further actions are
    * required or a fatal error occurs
    */
  protected def performAnalysis(): Unit = {
    try {
      setRunning(true)
      info("inserting analysis run into database")
      device.withRunningFrida {
        Appium.withRunningAppium(app.id, conf, device) { appium =>
          this.checkStop()
          info(s"starting app ${app.id} for interface analysis")
          actor.onAppStartup(this)
          if (app.id != "EMPTY")
            device.startApp(app.id, noAppStartCheck)
          checkIfAppIsStillRunning(
            true
          ) // initial check if the app startup even worked
          this.checkStop()
          info("extracting start interface")
          var currentInterface =
            handlePostAppStartup("initial interface", appium, device)
          currentInterface.insert()
          var running = true
          while (running) {
            try {
              info("calling on actor to perform his magic")
              checkStop()
              actor.action(currentInterface)(this, appium) match {
                case Some(action) => // this there was a click
                  info("actor indicates that he has further actions to perform")
                  action.getLeadingTo match {
                    case Some(
                          value
                        ) => // this means there is a resulting app interface
                      running = true
                      currentInterface = value
                      value.insert()
                    case None => // this means the app closed itself and the actor is done
                      running = false
                  }
                  action.insert()
                case None => // this means the actor is done
                  info("actor indicates that he done")
                  running = false
              }
            } catch {
              case x: MissingInterfaceElement =>
                addEncounteredError(x, currentInterface.getOptionalId)
                running = false
                throw AnalysisFatal(x.getMessage)
              // THIS IS THE WRONG PLACE TO CATCH ANALYSIS TIMEOUT!
              case x: InterfaceAnalysisCondition =>
                addEncounteredError(x, currentInterface.getOptionalId)
                running = false
            }
          }
        }
      }
    } catch {
      case x: FridaDied =>
        addEncounteredError(x)
        running = false
      case x: AnalysisTookTooLong =>
        error("I took too long, killing myself now")
        addEncounteredError(x, None)
        // while this is the right place to catch it we have to rethrow it to not continue with this app and actor
        throw AnalysisFatal(x.getMessage)
      // this is not the place to dismiss app closed itself - either it is possible expected behavior by the
      // actor or this ends the analysis of this app as the actor does not know how to deal with this
      case x: AppClosedItself =>
        this.addEncounteredError(x, None)
        throw AnalysisFatal(x.getMessage) // rethrow as it is analysis fatal
    } finally {
      if (activeTrafficCollection.nonEmpty) {
        warn(
          "after the analysis is done there is still an active traffic collection. Closing..."
        )
        stopTrafficCollection()
      }
      setRunning(false)
    }
  }

  /** inserts a new analysis record into the database
    *
    * Sets the analysis ID after successful insertion. Throws a RuntimeException
    * if the analysis has already been inserted
    */
  protected def insert(): Unit = {
    if (id.nonEmpty) {
      throw new RuntimeException(
        "This analysis already has been inserted - this is a severe logic bug"
      )
    }
    val experiment = Experiment.getCurrentExperiment.id
    Postgres.withDatabaseSession { implicit session =>
      id = sql"""INSERT INTO interfaceanalysis (
                              experiment,
                              app_id,
                              app_version,
                              app_os,
                              description
                              )
                    VALUES (
                            $experiment,
                            ${app.id},
                            ${app.version},
                            ${app.getOsString},
                            $description
                            )
                    RETURNING id
                 """
        .map { entity =>
          entity.int("id")
        }
        .first
        .apply()
    }
  }

  /** marks the analysis as finished by updating the end time and success flag
    * in the database
    *
    * @param success
    *   true if the analysis completed successfully; false otherwise
    */
  protected def finish(success: Boolean): Unit = {
    Postgres.withDatabaseSession { implicit session =>
      sql"""UPDATE interfaceanalysis
                  SET
                      end_time = NOW(),
                      success = $success
                  WHERE
                       id = ${this.getId}""".update
        .apply()
    }
  }

  /** records an encountered error during the analysis
    *
    * Depending on the type of error, logs the error and inserts an error record
    * into the database
    *
    * @param err
    *   The error that occurred
    * @param interfaceid
    *   An optional interface ID associated with the error
    * @param silent
    *   If true, suppresses error logging
    */
  def addEncounteredError(
      err: Throwable,
      interfaceid: Option[Int] = None,
      silent: Boolean = false
  ): Unit = {
    err match {
      case x: InterfaceAnalysisCondition =>
        if (!silent) error(s"analysis encountered error ${err.getMessage}")
        Postgres.withDatabaseSession { implicit session =>
          sql"""INSERT INTO InterfaceAnalysisError (
                                        analysis,
                                        interface,
                                        message,
                                        stacktrace
                                        )
                  VALUES (
                          ${this.getId},
                          ${interfaceid.orNull},
                          ${x.getMessage},
                          ${x.getStacktraceString}
                          )""".update
            .apply()
        }
      case err =>
        addEncounteredError(UncaughtCondition(err), interfaceid)
        if (
          !err.isInstanceOf[AnalysisTookTooLong]
        ) // we somewhat expected analysis took to long errors, need no stacktrace
          error(s"error was unexpected:\n${err.getStackTrace.mkString("\n")}")
    }
  }

}

object Analysis extends LogSupport {

  /** exception indicating that the analysis took too long
    */
  class AnalysisTookTooLong extends Throwable {
    override def getMessage: String = "analysis took too long, kill yeself!"
  }

  private var currentAnalysis: Option[Analysis] = None

  /** sets the current active analysis
    *
    * @param analysis
    *   the Analysis instance to set as current
    */
  private def setCurrentAnalysis(analysis: Analysis): Unit = synchronized {
    currentAnalysis = Some(analysis)
  }

  /** signals the current analysis to stop by setting its stop flag
    *
    * @throws FatalError
    *   if no analysis is currently running
    */
  private def stopCurrentAnalysis(): Unit = synchronized {
    currentAnalysis match {
      case Some(value) => value.setStop()
      case None =>
        throw new FatalError("trying to stop a non existing analysis")
    }
  }

  /** unsets the current analysis after it has completed
    *
    * @throws FatalError
    *   if attempting to unset an analysis that is still running
    */
  private def unsetCurrentAnalysis(): Unit = synchronized {
    currentAnalysis match {
      case Some(analysis) if analysis.getRunning =>
        throw new FatalError("trying to unset running analysis")
      case Some(analysis) if !analysis.getRunning =>
      // everything is fine
      case Some(_) =>
        throw new FatalError(
          "this is a logic flaw and must never happen (see two matches before)"
        )
      case None =>
        // this is really weird and indicates something fundamentally flawed in the logic
        warn(
          "unsetting an analysis that has never been started or created ... skip!"
        )
    }
    currentAnalysis = None
  }

  /** runs the analysis for a given mobile app
    *
    * Ensures that the device is ready, installs the app if necessary, and
    * repeatedly invokes the actor plugin to interact with the app until the
    * actor signals completion. Also performs cleanup tasks such as uninstalling
    * the app and clearing stuck modals.
    *
    * @param actor
    *   The actor plugin to drive the analysis
    * @param app
    *   The mobile app to analyze
    * @param device
    *   The device on which the app is executed
    * @param conf
    *   The configuration settings
    * @param noAppStartCheck
    *   Flag to bypass the app startup check
    */
  def runAnalysis(
      actor: ActorPlugin,
      app: MobileApp,
      device: Device,
      conf: Config,
      noAppStartCheck: Boolean
  ): Unit = {
    info(s"running analysis for ${app.toString}")
    device.ensureDevice()
    try {
      val logger = this.logger
      Experiment.withExperimentErrorLogging {
        app.insert()
        val analysis = Future {
          var continue = false
          try {
            if (app.id != "EMPTY") {
              device.installApp(app)
              device.setAppPermissions(app.id)
            }
            do {
              logger.info(
                s"setting up analysis ${actor.getDescription} for app $app"
              )
              val analysis =
                new Analysis(
                  actor.getDescription,
                  app,
                  actor,
                  device,
                  conf,
                  noAppStartCheck
                )
              setCurrentAnalysis(analysis)
              analysis.insert()
              try {
                analysis.performAnalysis()
                val (c, reset) = actor.restartApp
                continue = c
                if (reset && c) {
                  logger.info("actor requests app reset")
                  if (app.id != "EMPTY") {
                    device.uninstallApp(app.id)
                    device.installApp(app)
                    device.setAppPermissions(app.id)
                  }
                }
                analysis.finish(true)
              } catch {
                case x: SkipThisApp =>
                  info("got order to skip")
                  analysis.addEncounteredError(x, None, silent = true)
                  continue = false
                  analysis.finish(false)
                case x: UnableToStartApp =>
                  Experiment.addEncounteredError(x)
                  analysis.finish(false)
                  continue = false
                case x: StaleElementReferenceException =>
                  analysis.addEncounteredError(
                    new StaleInterfaceElement(x),
                    None
                  )
                  analysis.finish(false)
                  continue = false
                case x: WebDriverException =>
                  analysis.addEncounteredError(new WebDriverHissyFit(x), None)
                  analysis.finish(false)
                  continue = false
              }
            } while (continue)
          } catch {
            case x: AnalysisFatal =>
              Experiment.addEncounteredError(x)
              error(x.getMessage)
            case x: UnableToInstallApp =>
              Experiment.addEncounteredError(x)
            case x: UnableToUninstallApp =>
              Experiment.addEncounteredError(x)
          } finally {
            unsetCurrentAnalysis()
          }
        }
        try {
          Await.result(analysis, Duration(conf.timeoutMilli, MILLISECONDS))
        } catch {
          case _: TimeoutException =>
            error(s"the analysis of $app took too long, trying to kill")
            stopCurrentAnalysis()
            Await.result(analysis, Inf)
            error("kill success")
        } finally {
          try {
            if (app.id != "EMPTY") {
              device.uninstallApp(app.id)
            }
            device.clearStuckModals()
          } catch {
            case x: UnableToUninstallApp =>
              Experiment.addEncounteredError(x)
          }
        }
      }
      info(s"analysis of app $app is done")
    } finally {}
  }
}
