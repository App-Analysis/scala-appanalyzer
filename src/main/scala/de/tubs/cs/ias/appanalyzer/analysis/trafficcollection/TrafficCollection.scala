package de.tubs.cs.ias.appanalyzer.analysis.trafficcollection

import de.tubs.cs.ias.appanalyzer.Config
import de.tubs.cs.ias.appanalyzer.database.Postgres
import de.tubs.cs.ias.appanalyzer.platform.exceptions.FatalError
import scalikejdbc.scalikejdbcSQLInterpolationImplicitDef

import scala.sys.process.{Process, ProcessLogger}

class TrafficCollection(analysis: Int,
                        interface: Option[Int],
                        comment: String,
                        conf: Config,
                        dumb: Boolean = false) {

  private var id: Option[Int] = None
  private var mitmproxy: Option[Process] = None

  private def startMitmProxy(): Unit = {
    assert(mitmproxy.isEmpty)
    val cmd =
      s"${conf.mitm.path} -s ${conf.mitm.addonScript} --set run=${id.get}"
    mitmproxy = Some(
      Process(
        cmd,
        None,
        "POSTGRES_HOST" -> conf.db.host,
        "HOST_PORT" -> conf.db.port.toString,
        "POSTGRES_DB" -> conf.db.name,
        "POSTGRES_USER" -> conf.db.user,
        "POSTGRES_PASSWORD" -> conf.db.pwd
      ).run(ProcessLogger(_ => ())))
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
    mitmproxy = Some(
      Process(s"${conf.mitm.path}").run(ProcessLogger(_ => (), _ => ())))
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

  def startNewTrafficCollection(analysis: Int,
                                interface: Option[Int],
                                comment: String,
                                conf: Config): TrafficCollection = {
    activeTrafficCollection match {
      case Some(_) =>
        throw new FatalError("there is already a traffic collection running")
      case None =>
        activeTrafficCollection = Some(
          new TrafficCollection(analysis,
                                interface,
                                comment,
                                conf,
                                dumb = false))
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
          new TrafficCollection(-1, null, "dumb", conf, dumb = true))
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
