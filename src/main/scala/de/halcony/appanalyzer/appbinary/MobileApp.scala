package de.halcony.appanalyzer.appbinary

import de.halcony.appanalyzer.database.Postgres
import de.halcony.appanalyzer.platform.PlatformOperatingSystems
import PlatformOperatingSystems.{ANDROID, PlatformOS, IOS}
import de.halcony.appanalyzer
import scalikejdbc.scalikejdbcSQLInterpolationImplicitDef
import wvlet.log.LogSupport

case class MobileApp(
    id: String,
    version: String,
    os: PlatformOS,
    private val _path: String
) extends LogSupport {
  val path: String = s""""${_path}""""
  val serializedPath: String = _path

  /** get the os in string format
    *
    * @return
    *   the os as a string
    */
  def getOsString: String = os match {
    case PlatformOperatingSystems.ANDROID                  => "android"
    case appanalyzer.platform.PlatformOperatingSystems.IOS => "ios"
  }

  /** insert the app into the connected database
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
    * @param os
    *   the os string
    * @return
    *   the corresponding enum field
    */
  def stringToOsEnum(os: String): PlatformOS = {
    os match {
      case "android" => ANDROID
      case "ios"     => IOS
    }
  }

}
