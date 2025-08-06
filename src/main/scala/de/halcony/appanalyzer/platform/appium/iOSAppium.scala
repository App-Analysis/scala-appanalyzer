package de.halcony.appanalyzer.platform.appium

import de.halcony.appanalyzer.Config
import de.halcony.appanalyzer.platform.exceptions.FatalError
import io.appium.java_client.ios.IOSDriver
import io.appium.java_client.{AppiumBy, AppiumDriver}
import org.openqa.selenium.remote.DesiredCapabilities
import org.openqa.selenium.{By, WebElement}
import wvlet.log.LogSupport

import java.net.URL
import java.util
import scala.jdk.CollectionConverters.CollectionHasAsScala

class iOSAppium(conf: Config) extends Appium with LogSupport {

  override protected var driver: Option[AppiumDriver] = None

  /** extract all UI elements from the iOS app's UI
  *
  * @return a list of WebElement objects representing the UI elements
  */
  override def getAllElements: List[WebElement] = {
    info("extracting elements from UI")
    val ret = this.driver.get
      .asInstanceOf[IOSDriver]
      .findElements(AppiumBy.iOSNsPredicateString("TRUEPREDICATE"))
      .asScala
      .toList
    info(s"extracted ${ret.length} elements")
    ret
  }

  /** repeatedly handle alert dialogs by detecting and interacting with them
  *
  * @return true if at least one alert was successfully addressed, false otherwise
  */
  def adressingAllAlerts(): Boolean = {
    val ALERT_TEXT_REGEXP = conf.ios.permissionPopup.text.map(_.r.unanchored)
    val ALLOW_BUTTON_TEXT =
      conf.ios.permissionPopup.allowButton.map(_.r.anchored)
    val DISALLOW_BUTTON_TEXT =
      conf.ios.permissionPopup.dontAllowButton.map(_.r.anchored)
    var continue = false
    var gotRid = false
    do {
      val alert: Option[WebElement] =
        this.driver.get
          .asInstanceOf[IOSDriver]
          .findElements(By.className("XCUIElementTypeAlert"))
          .asScala
          .toList
          .find(elem =>
            ALERT_TEXT_REGEXP.exists(regexp => regexp.matches(elem.getText))
          )
      alert match {
        case Some(alertElement) =>
          continue = true
          info("we detected an alert box")
          val buttons = alertElement
            .findElements(By.className("XCUIElementTypeButton"))
            .asScala
            .toList
          val allow = buttons.filter(button =>
            ALLOW_BUTTON_TEXT.exists(regexp =>
              regexp.matches(button.getText.toLowerCase.trim)
            )
          )
          val disallow = buttons.filter(button =>
            DISALLOW_BUTTON_TEXT.exists(regexp =>
              regexp.matches(button.getText.toLowerCase.trim)
            )
          )
          if (!(allow.length == 1 && disallow.length == 1)) {
            throw new FatalError(
              s"found alert but ${allow.length} allow buttons and ${disallow.length} disallow buttons"
            )
          } else {
            conf.ios.permissionPopup.interaction match {
              case "allow" =>
                info("allowing")
                allow.head.click()
                gotRid = true
              case "deny" =>
                info("disallowing")
                disallow.head.click()
                gotRid = true
              case _ =>
            }
          }
        case None =>
          continue = false
          info("we did not detect an alert box")
      }
    } while (continue)
    gotRid
  }

  /** dismiss all alert dialogs currently present on the screen based on configuration
  *
  * @param conf the configuration containing alert text patterns and button settings
  * @return true if any alert was dismissed, false otherwise
  */
  def getRidOfAlerts(conf: Config): Boolean = {
    val alert: util.List[WebElement] = this.driver.get
      .asInstanceOf[IOSDriver]
      .findElements(By.className("XCUIElementTypeAlert"))
    val ALERT_TEXT_REGEXP = conf.ios.permissionPopup.text.map(_.r.unanchored)
    val ALLOW_BUTTON_TEXT =
      conf.ios.permissionPopup.allowButton.map(_.r.anchored)
    val DISALLOW_BUTTON_TEXT =
      conf.ios.permissionPopup.dontAllowButton.map(_.r.anchored)
    var gotRid: Boolean = false
    alert.forEach { alertElement =>
      val alertElementText = alertElement.getText.toLowerCase.trim
      if (
        ALERT_TEXT_REGEXP.exists(regexp => regexp.matches(alertElementText))
      ) {
        info("we detected an alert box matching our config")
        val buttons = alertElement
          .findElements(By.className("XCUIElementTypeButton"))
          .asScala
          .toList
        val allow = buttons.filter(button =>
          ALLOW_BUTTON_TEXT.exists(regexp =>
            regexp.matches(button.getText.toLowerCase.trim)
          )
        )
        val disallow = buttons.filter(button =>
          DISALLOW_BUTTON_TEXT.exists(regexp =>
            regexp.matches(button.getText.toLowerCase.trim)
          )
        )
        if (!(allow.length == 1 && disallow.length == 1)) {
          throw new FatalError(
            s"found alert but ${allow.length} allow buttons and ${disallow.length} disallow buttons"
          )
        }
        conf.ios.permissionPopup.interaction match {
          case "allow" =>
            info("allowing")
            allow.head.click()
            gotRid = true
          case "deny" =>
            info("disallowing")
            disallow.head.click()
            gotRid = true
          case _ =>
        }
      } else {
        info("no push notification detected")
      }
    }
    gotRid
  }

  /** establish a connection to the iOS device using Appium
  *
  * Configures the desired capabilities and initializes the Appium driver for iOS
  *
  * @param appId the identifier of the application to be set in the desired capabilities
  */
  override protected def connect(appId: String): Unit = {
    val capabilities = new DesiredCapabilities()
    capabilities.setCapability("appium:automationName", "XCUITest")
    capabilities.setCapability("appium:platformName", "iOS")
    capabilities.setCapability("appium:platformVersion", conf.ios.osv)
    capabilities.setCapability("appium:deviceName", conf.ios.deviceName)
    capabilities.setCapability("appium:xcodeOrgId", conf.ios.getXcodeOrgId)
    capabilities.setCapability("appium:xcodeSigningId", conf.ios.xcodeSigningId)
    capabilities.setCapability("appium:udid", "auto") // see if this works
    capabilities.setCapability("appium:appPackage", appId)
    capabilities.setCapability("appium:autoLaunch", false)
    capabilities.setCapability("appium:noReset", true) // no idea what
    capabilities.setCapability("appium:fullReset", false) // those are doing
    capabilities.setCapability(
      "appium:waitForIdleTimeout",
      2
    ) // reduce idling time requirement
    capabilities.setCapability(
      "newCommandTimeout",
      450
    ) // this means it takes 5 minutes before appium quits
    val driver = new IOSDriver(
      new URL(s"http://${this.getServer}:${this.getPort}/"),
      capabilities
    )
    driver.getBatteryInfo.getState.toString // cargo cult to ensure that appium has started
    this.driver = Some(driver)
  }
}
