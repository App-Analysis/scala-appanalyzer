package de.halcony.appanalyzer

import de.halcony.appanalyzer.analysis.plugin.PluginManager.HasPluginManagerConfiguration
import de.halcony.appanalyzer.analysis.plugin.{
  PluginManagerConfiguration,
  RemotePluginConfig
}
import spray.json.{DefaultJsonProtocol, JsonParser, RootJsonFormat}

import scala.io.Source

case class DevicePreparation(
    clipboard: String,
    latitude: Double,
    longitude: Double,
    altitude: Double
)

case class AndroidAnalysis(
    dexdump: String,
    appium: Boolean,
    apkanalyzer: String,
    adb: String,
    objection: String,
    osVersion: String
)

case class iOSPermissionPopup(
    interaction: String,
    text: List[String],
    allowButton: List[String],
    dontAllowButton: List[String]
)

case class iOS(
    rootpwd: String,
    ip: String,
    appium: Boolean,
    ideviceinstaller: String,
    ideviceinfo: String,
    fridaps: String,
    deviceName: String,
    xcodeOrgId: String,
    osv: String,
    xcodeSigningId: String,
    permissionPopup: iOSPermissionPopup
) {

  def getXcodeOrgId: String = {
    val source: Source = Source.fromFile(xcodeOrgId)
    try {
      source.getLines().mkString("").trim
    } finally {
      source.close()
    }
  }
}

case class Mitmproxy(path: String, addonScript: String)

case class Database(
    host: String,
    port: Int,
    name: String,
    user: String,
    pwd: String
)

case class Emulator(
    emulator: String,
    avd: String,
    snapshot: Option[String],
    proxyIP: Option[String],
    proxyPort: Option[String]
)

case class Config(
    timeoutMilli: Long,
    verbose: Boolean,
    appium: String,
    tmpFolder: String,
    mitm: Mitmproxy,
    plugin: PluginManagerConfiguration,
    db: Database,
    devicePrep: DevicePreparation,
    emulator: Option[Emulator],
    android: AndroidAnalysis,
    ios: iOS,
    appiumURLExtension: String
) extends HasPluginManagerConfiguration {

  override def getPluginManagerConfiguration: PluginManagerConfiguration =
    plugin

}

object Config extends DefaultJsonProtocol {

  implicit val mitmproxyFormat: RootJsonFormat[Mitmproxy] = jsonFormat2(
    Mitmproxy
  )

  implicit val databaseFormat: RootJsonFormat[Database] = jsonFormat5(Database)

  implicit val androidApkAnalysisFormat: RootJsonFormat[AndroidAnalysis] =
    jsonFormat6(AndroidAnalysis)

  implicit val permissionPopupFormat: RootJsonFormat[iOSPermissionPopup] =
    jsonFormat4(iOSPermissionPopup)

  implicit val iosFormat: RootJsonFormat[iOS] = jsonFormat11(iOS)

  implicit val devicePreparationFormat: RootJsonFormat[DevicePreparation] =
    jsonFormat4(DevicePreparation)

  implicit val remotePluginConfigFormat: RootJsonFormat[RemotePluginConfig] =
    jsonFormat2(RemotePluginConfig)

  implicit val pluginManagerConfigurationFormat
      : RootJsonFormat[PluginManagerConfiguration] =
    jsonFormat2(PluginManagerConfiguration)

  implicit val emulatorFormat: RootJsonFormat[Emulator] =
    jsonFormat5(Emulator)

  implicit val configFormat: RootJsonFormat[Config] = jsonFormat12(Config.apply)

  /** given a file path parses the configuration file
    *
    * @param path
    *   the path to the configuration file
    * @return
    *   the parsed configuration object
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
