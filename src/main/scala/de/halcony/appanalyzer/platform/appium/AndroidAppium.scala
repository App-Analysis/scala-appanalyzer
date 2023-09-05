package de.halcony.appanalyzer.platform.appium

import de.halcony.appanalyzer.Config
import io.appium.java_client.AppiumDriver
import io.appium.java_client.android.AndroidDriver
import org.openqa.selenium.WebElement
import org.openqa.selenium.remote.DesiredCapabilities
import wvlet.log.LogSupport
import java.net.URL
import scala.annotation.nowarn

class AndroidAppium(conf: Config) extends Appium with LogSupport {

  override protected var driver: Option[AppiumDriver] = None

  override def getAllElements: List[WebElement] = {
    info("extracting elements from UI")
    val ret = super.getAllElements
    info(s"extracted ${ret.length} elements")
    ret
  }

  /**
    *
    * @param appId not required for android
    */
  override protected def connect(@nowarn appId: String): Unit = {
    info("connecting to appium on android")
    val capabilities = new DesiredCapabilities()
    capabilities.setCapability("platformName", "Android")
    capabilities.setCapability("automationName", "uiautomator2")
    capabilities.setCapability("deviceName", "ignored-on-android")
    capabilities.setCapability("platformVersion", conf.android.osVersion)
    capabilities.setCapability("autoGrantPermissions", true)
    capabilities.setCapability("appWaitForLaunch", false)
    capabilities.setCapability("appWaitActivity", "*")
    capabilities.setCapability("newCommandTimeout", 450) // this means it takes 5 minutes before appium quits
    val driver = new AndroidDriver(
      new URL(s"http://${this.getServer}:${this.getPort}"),
      capabilities)
    driver.getBatteryInfo.getState.toString // cargo cult to ensure that appium has started
    this.driver = Some(driver)
  }

}
