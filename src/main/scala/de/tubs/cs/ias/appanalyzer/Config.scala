package de.tubs.cs.ias.appanalyzer

import spray.json.{DefaultJsonProtocol, JsonParser, RootJsonFormat}
import scala.io.Source

case class DevicePreparation(clipboard: String,
                             latitude: Double,
                             longitude: Double,
                             altitude: Double)

case class AndroidAnalysis(dexdump: String,
                           apkanalyzer: String,
                           adb: String,
                           objection: String,
                           osVersion: String)

case class iOSPermissionPopup(interaction: String,
                              text: List[String],
                              allowButton: List[String],
                              dontAllowButton: List[String])

case class iOS(rootpwd: String,
               ip: String,
               ideviceinstaller: String,
               ideviceinfo: String,
               fridaps: String,
               deviceName: String,
               xcodeOrgId: String,
               osv: String,
               xcodeSigningId: String,
               permissionPopup: iOSPermissionPopup) {

  def getXcodeOrgId: String = {
    val source: Source = Source.fromFile(xcodeOrgId)
    try {
      source.getLines().mkString("").trim
    } finally {
      source.close()
    }
  }

}

case class ConsentDialogAnalysis(indicatorsFile: String,
                                 keywordThreshold: Int,
                                 maxSizeFactor: Double,
                                 maxColorDeltaE: Int)

case class Mitmproxy(path: String, addonScript: String)

case class Database(host: String,
                    port: Int,
                    name: String,
                    user: String,
                    pwd: String)

case class Config(timeoutMilli: Long,
                  verbose: Boolean,
                  initialCollectionTimeMilli: Long,
                  appium: String,
                  tmpFolder: String,
                  mitm: Mitmproxy,
                  db: Database,
                  consentDialogAnalysis: ConsentDialogAnalysis,
                  devicePrep: DevicePreparation,
                  android: AndroidAnalysis,
                  ios: iOS)

object Config extends DefaultJsonProtocol {

  implicit val mitmproxyFormat: RootJsonFormat[Mitmproxy] = jsonFormat2(
    Mitmproxy)

  implicit val databaseFormat: RootJsonFormat[Database] = jsonFormat5(Database)

  implicit val androidApkAnalysisFormat: RootJsonFormat[AndroidAnalysis] =
    jsonFormat5(AndroidAnalysis)

  implicit val permissionPopupFormat: RootJsonFormat[iOSPermissionPopup] =
    jsonFormat4(iOSPermissionPopup)

  implicit val iosFormat: RootJsonFormat[iOS] = jsonFormat10(iOS)

  implicit val devicePreparationFormat: RootJsonFormat[DevicePreparation] =
    jsonFormat4(DevicePreparation)

  implicit val consentDialogAnalysisFormat
    : RootJsonFormat[ConsentDialogAnalysis] = jsonFormat4(ConsentDialogAnalysis)

  implicit val configFormat: RootJsonFormat[Config] = jsonFormat11(Config.apply)

  /** given a file path parses the configuration file
    *
    * @param path the path to the configuration file
    * @return the parsed configuration object
    */
  def parse(path: String): Config = {
    val source = Source.fromFile(path)
    try {
      val string = source.getLines().mkString("\n")
      JsonParser(string).convertTo[Config]
    } finally {
      source.close()
    }
  }

}
