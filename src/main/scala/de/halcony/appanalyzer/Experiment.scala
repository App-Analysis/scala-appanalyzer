package de.halcony.appanalyzer

import de.halcony.appanalyzer.appbinary.MobileApp
import de.halcony.appanalyzer.database.Postgres
import de.halcony.appanalyzer.platform.exceptions.FatalError
import scalikejdbc.scalikejdbcSQLInterpolationImplicitDef
import wvlet.log.LogSupport

case class Experiment(id: Int, description: String)

object Experiment extends LogSupport {

  private var currentExperiment: Option[Experiment] = None

  /** inserting a given error into the database linked to the running experiment
    *
    * @param x the throwable to be stored
    * @param silent whether or not to print an error log
    * @param stackTrace whether or not the error log should also print the stacktrace
    */
  def addEncounteredError(
      x: Throwable,
      silent: Boolean = false,
      stackTrace: Boolean = true
  ): Unit = {
    if (!silent) error(x.getClass.toString + " " + x.getMessage)
    if (!silent && stackTrace) error(x.getStackTrace.mkString("\n"))
    Postgres.withDatabaseSession { implicit session =>
      val experimentId = currentExperiment
        .getOrElse(throw new RuntimeException("no current experiment"))
        .id
      sql"""INSERT INTO Experimenterror(
                            experiment,
                            message,
                            stacktrace
                            )
                  VALUES (
                          $experimentId,
                          ${x.getMessage},
                          ${x.getStackTrace.mkString("\n")}
                          )""".update.apply()
    }
  }

  /** macro ensures that any error not caught inside will be stored with the running experiment
    *
    * this is a last line of catching errors, will throw a fatal error as any error should not reach this
    *
    * @param func the code to be enclosed
    * @tparam T the return type of the wrapped code
    * @return the return of the wrapped code
    */
  def withExperimentErrorLogging[T](func: => T): T = {
    try {
      func
    } catch {
      case x: Throwable =>
        addEncounteredError(x)
        throw new FatalError(
          "we encountered an exception on the experiment layer - this is fatal"
        )
    }
  }

  /** inserts a new experiment into the database and stores the corresponding object
    *
    * @param description the description of the experiment
    * @return the experiment object
    */
  def createNewExperiment(description: String): Experiment = {
    debug("creating new experiment")
    val id = Postgres.withDatabaseSession { implicit session =>
      sql"""INSERT INTO Experiment (
                                description
                                )
            VALUES (
                    $description
            ) RETURNING id"""
        .map(_.int("id"))
        .first
        .apply()
        .get
    }
    currentExperiment = Some(Experiment(id, description))
    currentExperiment.get
  }

  /** load a specified experiment from the database
    *
    * @param id the id of the experiment to retrieve
    * @return the experiment
    */
  def loadExperiment(id: Int): Experiment = {
    debug("loading experiment")
    val (experimentId, description): (Int, String) =
      Postgres.withDatabaseSession { implicit session =>
        sql"SELECT id,description FROM Experiment WHERE id = $id"
          .map(row => (row.int("id"), row.string("description")))
          .first
          .apply()
          .get
      }
    currentExperiment = Some(Experiment(experimentId, description))
    currentExperiment.get
  }

  /** returns the currently active experiment
    *
    * @return the currently active experiment
    */
  def getCurrentExperiment: Experiment = {
    currentExperiment.getOrElse(
      throw new RuntimeException(
        "there is no current experiment, load or create a new one first"
      )
    )
  }

  /** @return a list of all app already having been analyzed in some form
    */
  def getAnalyzedApps: List[MobileApp] = {
    assert(this.currentExperiment.nonEmpty)
    val experimentId = this.currentExperiment
      .getOrElse(throw new RuntimeException("need loaded experiment"))
      .id
    Postgres.withDatabaseSession { implicit session =>
      sql"""SELECT
                   app_id,
                   app_version,
                   app_os
            FROM InterfaceAnalysis
            WHERE
                 experiment = $experimentId"""
        .map { row =>
          appbinary.MobileApp(
            row.string("app_id"),
            row.string("app_version"),
            MobileApp.stringToOsEnum(row.string("app_os")),
            "N/A"
          )
        }
        .toList
        .apply()
    }
  }

  /** delete the current experiment content from the experiment
    */
  def deleteCurrentExperiment(): Unit = {
    debug("deleting experiment")
    Postgres.withDatabaseSession { implicit session =>
      val id = currentExperiment.get.id
      sql"DELETE FROM Experiment WHERE id = $id".update
        .apply()
    }
  }

}
