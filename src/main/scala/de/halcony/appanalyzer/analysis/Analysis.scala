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
import de.halcony.appanalyzer.platform.PlatformOS
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

class Analysis(
    description: String,
    app: MobileApp,
    actor: ActorPlugin,
    device: Device,
    conf: Config
) extends LogSupport {

  private var id: Option[Int] = None
  private var activeTrafficCollection: Option[TrafficCollection] = None
  private var trafficCollectionStart: Option[ZonedDateTime] = None
  private var stop: Boolean = false
  private var running: Boolean = false
  private var collectInterfaceElements: Boolean = true

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

  def deviceIsRooted: Boolean = synchronized {
    device.ROOT
  }

  def getRunning: Boolean = synchronized {
    running
  }

  private def setStop(): Unit = synchronized {
    stop = true
  }

  def checkStop(): Unit = synchronized {
    if (stop) {
      throw new AnalysisTookTooLong()
    }
  }

  def getId: Int = id.get

  def getCurrentApp: MobileApp = app

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
              parameters
            )
        )
        trafficCollectionStart = Some(ZonedDateTime.now())
    }
  }

  def getTrafficCollectionStart: ZonedDateTime =
    trafficCollectionStart.getOrElse(
      throw new FatalError(
        "requesting traffic collection start time without ever starting traffic collection"
      )
    )

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

  def checkIfAppIsStillRunning(fail: Boolean): Boolean = {
    if (app.id == "EMPTY") {
      true
    } else {
      val running = device.getForegroundAppId match {
        case Some(value) =>
          if (value == app.id) {
            true
          } else {
            error(
              s"the foreground app is $value but we expected ${app.id} - this indicates that the app closed itself"
            )
            false
          }
        case None => false
      }
      if (!running && fail) throw AppClosedItself(app.id)
      running
    }
  }

  private def handlePostAppStartup(
      interfaceComment: String,
      appium: Appium,
      device: Device
  ): Interface = {
    device.PLATFORM_OS match {
      case PlatformOS.Android =>
        interaction.Interface(
          this,
          appium,
          flat = !collectInterfaceElements,
          interfaceComment
        ) // nothing to do here
      case platform.PlatformOS.iOS =>
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
            device.startApp(app.id)
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

  class AnalysisTookTooLong extends Throwable {
    override def getMessage: String = "analysis took too long, kill yeself!"
  }

  private var currentAnalysis: Option[Analysis] = None

  private def setCurrentAnalysis(analysis: Analysis): Unit = synchronized {
    currentAnalysis = Some(analysis)
  }

  private def stopCurrentAnalysis(): Unit = synchronized {
    currentAnalysis match {
      case Some(value) => value.setStop()
      case None =>
        throw new FatalError("trying to stop a non existing analysis")
    }
  }

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

  def runAnalysis(
      actor: ActorPlugin,
      app: MobileApp,
      device: Device,
      conf: Config
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
                new Analysis(actor.getDescription, app, actor, device, conf)
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
