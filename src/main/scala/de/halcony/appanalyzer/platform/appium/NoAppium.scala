package de.halcony.appanalyzer.platform.appium

import io.appium.java_client.AppiumDriver
import org.openqa.selenium.WebElement
import wvlet.log.LogSupport

import java.awt.image.BufferedImage

class NoAppium() extends Appium with LogSupport {

  warn("created no appium API - no App interaction/screenshotting possible")

  override protected var driver: Option[AppiumDriver] = None

  override protected def startAppiumServer(appium: String): Unit = {}

  override protected def stopAppiumServer(): Unit = {}

  override protected def connect(appId: String): Unit = {}

  override def stop(): Unit = {}

  override def getAllElements: List[WebElement] = List()

  override def findElementsByXPath(xpath: String): List[WebElement] = List()

  override def takeScreenshot: Option[BufferedImage] = Some(
    new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)
  )

  override def takeElementScreenshot(
      element: WebElement
  ): Option[BufferedImage] = Some(
    new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)
  )

  override def setClipboardContent(content: String): Unit = {}

}
