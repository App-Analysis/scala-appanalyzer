import de.halcony.appanalyzer.Config
import de.halcony.appanalyzer.analysis.plugin.RemotePluginConfig
import org.scalatest.flatspec._
import org.scalatest.matchers._

class AppAnalyzerConfigSpec extends AnyFlatSpec with should.Matchers {
  "Config" should "be loaded" in {
    val conf = Config.parse("example.config.json")
    conf.timeoutMilli should be(900000)
    conf.verbose should be(false)
    conf.appium should be("appium")
    conf.appiumURLExtension should be("wd/hub/")
    conf.tmpFolder should be("/tmp/folder/")
    conf.mitm.path should be("mitmdump")
    conf.mitm.addonScript should be(
      "./resources/trafficCollection/mitm-addon.py"
    )
    conf.plugin.folder should be("./plugins/")
    conf.plugin.available should be(
      Map(
        "TrafficCollection" -> RemotePluginConfig(
          "simkoc",
          "appanalyzer-plugin-trafficcollection"
        )
      )
    )

    val emulator = conf.emulator
    emulator match {
      case Some(emulator) =>
        emulator.emulator should be("/path/to/emulator")
        emulator.avd should be("name-of-avd")
        emulator.snapshot should be(Some("thesnapshottouseifset"))
        emulator.proxyIP should be(Some("theipoftheproxyifset"))
        emulator.proxyPort should be(Some("theportoftheproxyifset"))
      case None => print("emulator is optional, in this case None")
    }

    val email = conf.email
    email match {
      case Some(email) =>
        email.host should be("smtp.gmail.com")
        email.port should be(587)
        email.user should be("ad\\exchange")
        email.email match {
          case Some(email: String) => email should be("exchange@optional.org")
          case None => print("email is optional, in this case None")
        }
        email.password should be("")
      case None => print("email is optional, in this case None")
    }

    conf.db.host should be("localhost")
    conf.db.port should be(5432)
    conf.db.name should be("db_name")
    conf.db.user should be("db_user")
    conf.db.pwd should be("db_pwd")
    conf.devicePrep.clipboard should be("sd325fEr23q2")
    conf.devicePrep.latitude should be(16.0000)
    conf.devicePrep.longitude should be(17.000)
    conf.devicePrep.altitude should be(100.00)
    conf.android.dexdump should be("dexdump")
    conf.android.adb should be("adb")
    conf.android.appium should be(true)
    conf.android.objection should be("objection")
    conf.android.osVersion should be("12")
    conf.android.apkanalyzer should be("apkanalyzer")
    conf.ios.appium should be(false)
    conf.ios.rootpwd should be("alpine")
    conf.ios.ip should be("iPhone IP")
    conf.ios.ideviceinstaller should be("ideviceinstaller")
    conf.ios.ideviceinfo should be("ideviceinfo")
    conf.ios.fridaps should be("frida-ps")
    conf.ios.deviceName should be("Device Name")
    conf.ios.xcodeOrgId should be("xoodeOrgId")
    conf.ios.xcodeSigningId should be("iPhone Developer")
    conf.ios.osv should be("14.5")
    conf.ios.permissionPopup.interaction should be("deny")
    conf.ios.permissionPopup.text should be(
      List("would like to", "wants to join", "turn on bluetooth to allow")
    )
    conf.ios.permissionPopup.allowButton should be(
      List("allow", "ok", "join", "settings", "allow access to all photos")
    )
    conf.ios.permissionPopup.dontAllowButton should be(
      List("don.t allow", "close", "cancel")
    )
  }
}
