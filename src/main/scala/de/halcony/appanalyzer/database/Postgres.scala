package de.halcony.appanalyzer.database

import de.halcony.appanalyzer.Config
import scalikejdbc._

object Postgres {

  private var poolSet: Boolean = false
  private val POOL_NAME = "appanalyzer"

  def initializeConnectionPool(conf: Config): Unit = {
    if (!poolSet) {
      val settings: ConnectionPoolSettings = ConnectionPoolSettings(
        initialSize = 10,
        maxSize = 10,
        driverName = "org.postgresql.Driver")
      val url =
        s"jdbc:postgresql://${conf.db.host}:${conf.db.port}/${conf.db.name}"
      ConnectionPool.add(POOL_NAME, url, conf.db.user, conf.db.pwd, settings)
      poolSet = true
    }
  }

  def withDatabaseSession[T](func: DBSession => T): T = {
    if (poolSet) {
      using(ConnectionPool(POOL_NAME).borrow()) { con =>
        DB(con).localTx { session =>
          func(session)
        }
      }
    } else {
      throw new RuntimeException(
        "there is no postgres connection pool, initialize first")
    }
  }

}
