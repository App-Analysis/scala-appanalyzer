package de.halcony.appanalyzer.analysis.trafficcollection

import de.halcony.appanalyzer.Config
import de.halcony.appanalyzer.database.Postgres
import de.halcony.appanalyzer.platform.exceptions.FatalError
import scalikejdbc.scalikejdbcSQLInterpolationImplicitDef
import wvlet.log.LogSupport

import scala.sys.process.{Process, ProcessLogger}

class TrafficCollection(
    analysis: Int,
    interface: Option[Int],
    comment: String,
    conf: Config,
    dumb: Boolean = false,
    parameters: Option[Seq[String]] = None
) extends LogSupport {

  private var id: Option[Int] = None
  private var mitmproxy: Option[Process] = None

  private def startMitmProxy(): Unit = {
    assert(mitmproxy.isEmpty)
    var cmd_list: Vector[String] = Vector(
      conf.mitm.path,
      "-s",
      conf.mitm.addonScript,
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

  private def stopMitmProxy(): Unit = {
    assert(mitmproxy.nonEmpty)
    mitmproxy.get.destroy()
  }

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

  protected def startDumbProxy(): Unit = {
    info("starting dump proxy")
    mitmproxy = Some(
      Process(s"""${conf.mitm.path} --ignore-hosts ".*""")
        .run(ProcessLogger(_ => (), _ => ()))
    )
  }

  protected def start(): Unit = {
    if (!dumb) {
      insert()
      startMitmProxy()
    } else
      startDumbProxy()
  }

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

object TrafficCollection {

  private var activeTrafficCollection: Option[TrafficCollection] = None

  def startNewTrafficCollection(
      analysis: Int,
      interface: Option[Int],
      comment: String,
      conf: Config,
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
            dumb = false,
            parameters = parameters
          )
        )
        activeTrafficCollection.get.start()
        activeTrafficCollection.get
    }
  }

  def startDumbTrafficCollection(conf: Config): TrafficCollection = {
    activeTrafficCollection match {
      case Some(_) =>
        throw new FatalError("there is already a traffic collection running")
      case None =>
        activeTrafficCollection = Some(
          new TrafficCollection(-1, null, "dumb", conf, dumb = true)
        )
        activeTrafficCollection.get.start()
        activeTrafficCollection.get
    }
  }

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
