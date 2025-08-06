package de.halcony.appanalyzer.analysis.trafficcollection

import de.halcony.appanalyzer.Config
import de.halcony.appanalyzer.database.Postgres
import de.halcony.appanalyzer.platform.exceptions.FatalError
import scalikejdbc.scalikejdbcSQLInterpolationImplicitDef
import wvlet.log.LogSupport

import scala.sys.process.{Process, ProcessLogger}

/** trafficCollection handles the collection of network traffic using mitmproxy
  *
  * This class supports two modes:
  *   - Normal mode: Inserts a record into the database, starts mitmproxy with
  *     an addon script, and later updates the record upon stopping
  *   - Dumb mode: Starts a basic mitmproxy process that ignores hosts, for
  *     simple traffic dumping
  *
  * @param analysis
  *   The analysis identifier
  * @param interface
  *   An optional interface identifier
  * @param comment
  *   A comment describing the traffic collection
  * @param conf
  *   The configuration object
  * @param port
  *   The port on which mitmproxy will run
  * @param dumb
  *   Flag indicating if the collection should run in dumb mode
  * @param parameters
  *   Optional additional parameters for mitmproxy
  */
class TrafficCollection(
    analysis: Int,
    interface: Option[Int],
    comment: String,
    conf: Config,
    port: String,
    dumb: Boolean = false,
    parameters: Option[Seq[String]] = None
) extends LogSupport {

  private var id: Option[Int] = None
  private var mitmproxy: Option[Process] = None

  /** starts the mitmproxy process with the configured addon script, port, and
    * run ID.
    */
  private def startMitmProxy(): Unit = {
    assert(mitmproxy.isEmpty)
    var cmd_list: Vector[String] = Vector(
      conf.mitm.path,
      "-s",
      conf.mitm.addonScript,
      "-p",
      port,
      "--set",
      s"run=${id.get}"
    )
    if (parameters.nonEmpty) {
      info("adding parameters to mitmproxy")
      cmd_list = cmd_list ++ parameters.get.toVector
    }
    info(s"starting mitmproxy with command: ${cmd_list.mkString(" ")}")
    mitmproxy = Some(
      Process(
        cmd_list.mkString(" "),
        None,
        "POSTGRES_HOST" -> conf.db.host,
        "HOST_PORT" -> conf.db.port.toString,
        "POSTGRES_DB" -> conf.db.name,
        "POSTGRES_USER" -> conf.db.user,
        "POSTGRES_PASSWORD" -> conf.db.pwd
      ).run(ProcessLogger(_ => ()))
    )
  }

  /** stops the running mitmproxy process.
    */
  private def stopMitmProxy(): Unit = {
    assert(mitmproxy.nonEmpty)
    mitmproxy.get.destroy()
  }

  /** inserts a new record into the Trafficcollection database table
    *
    * The record contains the analysis identifier, interface (if any), and a
    * comment
    */
  private def insert(): Unit = {
    id = Postgres.withDatabaseSession { implicit session =>
      sql"""INSERT INTO Trafficcollection (
                               analysis,
                               interface,
                               comment
                          )
               VALUES (
                       $analysis,
                       ${interface.orNull},
                       $comment
               )
               RETURNING id"""
        .map { entity =>
          entity.int("id")
        }
        .first
        .apply()
    }
  }

  /** starts a dumb proxy that ignores hosts.
    *
    * In dumb mode, a simple mitmproxy process is started with the
    * '--ignore-hosts' option, which causes it to dump traffic without filtering
    * or additional processing
    */
  protected def startDumbProxy(): Unit = {
    info("starting dump proxy")
    mitmproxy = Some(
      Process(s"""${conf.mitm.path} --ignore-hosts ".*""")
        .run(ProcessLogger(_ => (), _ => ()))
    )
  }

  /** starts the traffic collection process.
    *
    * In normal mode, a database record is inserted and mitmproxy is started
    * with the proper configuration. In dumb mode, a simple proxy is started to
    * dump traffic.
    */
  protected def start(): Unit = {
    if (!dumb) {
      insert()
      startMitmProxy()
    } else
      startDumbProxy()
  }

  /** stops the traffic collection process
    *
    * Terminates the running mitmproxy process. In normal mode, the
    * corresponding database record is updated with the current timestamp to
    * indicate the end of the traffic collection.
    */
  protected def stop(): Unit = {
    stopMitmProxy()
    if (!dumb) {
      Postgres.withDatabaseSession { implicit session =>
        sql"""UPDATE trafficcollection
              SET
                  stop = NOW()
              WHERE
                  id = ${id.get}
           """.update.apply()
      }
    }
  }

}

/** companion object for TrafficCollection.
  *
  * Provides helper methods to start and stop traffic collection processes,
  * ensuring that only one traffic collection runs at a time
  */
object TrafficCollection {

  private var activeTrafficCollection: Option[TrafficCollection] = None

  /** starts a new traffic collection in normal mode.
    *
    * @param analysis
    *   The analysis identifier
    * @param interface
    *   An optional interface identifier
    * @param comment
    *   A descriptive comment
    * @param conf
    *   The configuration object
    * @param port
    *   The port for mitmproxy
    * @param parameters
    *   Optional additional parameters for mitmproxy
    * @return
    *   The newly started TrafficCollection instance
    * @throws FatalError
    *   if there is already an active traffic collection
    */
  def startNewTrafficCollection(
      analysis: Int,
      interface: Option[Int],
      comment: String,
      conf: Config,
      port: String,
      parameters: Option[Seq[String]] = None
  ): TrafficCollection = {
    activeTrafficCollection match {
      case Some(_) =>
        throw new FatalError("there is already a traffic collection running")
      case None =>
        activeTrafficCollection = Some(
          new TrafficCollection(
            analysis,
            interface,
            comment,
            conf,
            port = port,
            dumb = false,
            parameters = parameters
          )
        )
        activeTrafficCollection.get.start()
        activeTrafficCollection.get
    }
  }

  /** starts a new traffic collection in dumb mode
    *
    * Creates a TrafficCollection instance that starts a proxy which ignores
    * hosts
    *
    * @param conf
    *   The configuration object
    * @return
    *   The newly started TrafficCollection instance in dumb mode
    * @throws FatalError
    *   if there is already an active traffic collection
    */
  def startDumbTrafficCollection(conf: Config): TrafficCollection = {
    activeTrafficCollection match {
      case Some(_) =>
        throw new FatalError("there is already a traffic collection running")
      case None =>
        activeTrafficCollection = Some(
          new TrafficCollection(
            -1,
            null,
            "dumb",
            conf,
            port = "8080",
            dumb = true
          )
        )
        activeTrafficCollection.get.start()
        activeTrafficCollection.get
    }
  }

  /** stops the currently active traffic collection
    *
    * @throws FatalError
    *   if there is no active traffic collection
    */
  def stopCurrentTrafficCollection(): Unit = {
    activeTrafficCollection match {
      case Some(value) =>
        value.stop()
        activeTrafficCollection = None
      case None =>
        throw new FatalError("there is no traffic collection running")
    }
  }
}
