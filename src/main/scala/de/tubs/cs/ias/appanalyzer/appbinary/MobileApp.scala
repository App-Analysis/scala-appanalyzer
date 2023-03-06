package de.tubs.cs.ias.appanalyzer.appbinary

import de.tubs.cs.ias.appanalyzer.database.Postgres
import de.tubs.cs.ias.appanalyzer.platform
import de.tubs.cs.ias.appanalyzer.platform.PlatformOS
import de.tubs.cs.ias.appanalyzer.platform.PlatformOS.{Android, PlatformOS, iOS}
import scalikejdbc.scalikejdbcSQLInterpolationImplicitDef
import wvlet.log.LogSupport

case class MobileApp(id: String, version: String, os: PlatformOS, path: String)
    extends LogSupport {

  /** get the os in string format
    *
    * @return the os as a string
    */
  def getOsString: String = os match {
    case PlatformOS.Android      => "android"
    case platform.PlatformOS.iOS => "ios"
  }

  /** insert the app into the connected database
    *
    */
  def insert(): Unit = {
    Postgres.withDatabaseSession { implicit session =>
      val osString = getOsString
      sql"""INSERT INTO App(
                app_id,
                version,
                os
                )
             VALUES (
                     $id,
                     $version,
                     $osString
                     )
             ON CONFLICT (
                 app_id,
                 version,
                 os)
             DO NOTHING""".update
        .apply()
    }
  }

  /** produce a readable string representing the app
    *
    * @return
    */
  override def toString: String = s"$id:$version@$getOsString"

}

object MobileApp {

  /** convert a given string into the corresponding os enum
    *
    * @param os the os string
    * @return the corresponding enum field
    */
  def stringToOsEnum(os: String): PlatformOS = {
    os match {
      case "android" => Android
      case "ios"     => iOS
    }
  }

}
