package de.halcony.appanalyzer.platform.appium

import de.halcony.appanalyzer
import de.halcony.appanalyzer.Config
import de.halcony.appanalyzer.platform.PlatformOperatingSystems
import de.halcony.appanalyzer.platform.device.Device
import de.halcony.appanalyzer.platform.exceptions.FatalError
import io.appium.java_client.AppiumDriver
import io.appium.java_client.clipboard.HasClipboard
import org.openqa.selenium.remote.ScreenshotException
import org.openqa.selenium.{By, OutputType, WebElement}
import wvlet.log.LogSupport

import java.awt.image.BufferedImage
import java.io.{BufferedWriter, ByteArrayInputStream, FileWriter}
import java.util.Base64
import javax.imageio.ImageIO
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.sys.process.{Process, ProcessLogger}

trait Appium extends LogSupport {
  protected def getPort: String = "4723"
  protected def getServer: String = "0.0.0.0"

  private val APPIUM_LOG_FILE = "./appium.log"

  private def writeToAppiumLogFile(otype: String, string: String): Unit =
    synchronized {
      val writer = new BufferedWriter(new FileWriter(APPIUM_LOG_FILE, true))
      try {

        writer.write(
          s"[${java.time.LocalDateTime.now().toString}][$otype] $string\n"
        )
      } finally {
        writer.flush()
        writer.close()
      }
    }

  // I force an override here to ensure a developer does not forget about this
  protected var driver: Option[AppiumDriver]

  protected var appiumProcess: Option[Process] = None

  /** start an appium server instance
    *
    * @param appium
    *   path to the appium installation on the local machine
    */
  protected def startAppiumServer(appium: String): Unit = {
    info("starting appium server")
    appiumProcess = Some(
      Process(appium).run(
        ProcessLogger(
          fout => writeToAppiumLogFile("INFO", fout),
          ferr => writeToAppiumLogFile("ERROR", ferr)
        )
      )
    )
    // we give appium 10 seconds to start up
    Thread.sleep(10000)
  }

  /** stops any running appium server instance
    */
  protected def stopAppiumServer(): Unit = {
    appiumProcess match {
      case Some(value) => value.destroy()
      case None        =>
    }
  }

  /** connect the driver
    */
  protected def connect(appId: String): Unit

  /** stop any running appium driver connection
    */
  protected def stop(): Unit = {
    driver match {
      case Some(value) =>
        value
          .quit() // if the appium server already stopped this will not terminate
      case None =>
        warn(
          "driver has not been created, could be due to an error being thrown just before creation"
        )
    }
  }

  def getAllElements: List[WebElement] = findElementsByXPath("//*")

  /** retrieve currently displayed elements by xpath string
    *
    * @param xpath
    *   the xpath to use
    * @return
    *   the list of elements
    */
  def findElementsByXPath(xpath: String): List[WebElement] = {
    this.driver.get
      .findElements(By.xpath(xpath))
      .asScala
      .toList
  }

  /** take a screenshot
    *
    * @return
    *   an option of a buffered image screenshot (none if screenshot failed)
    */
  def takeScreenshot: Option[BufferedImage] = {
    try {
      val bytes: Array[Byte] = Base64.getDecoder.decode(
        this.driver
          .getOrElse(throw new RuntimeException("appium is not connected"))
          .getScreenshotAs(OutputType.BASE64)
          .replaceAll("\n", "")
      )
      val inputStream = new ByteArrayInputStream(bytes)
      val image: BufferedImage = ImageIO.read(inputStream)
      Some(image)
    } catch {
      case _: ScreenshotException =>
        error("cannot take screenshot")
        None
      case x: Throwable =>
        error(
          s"taking screenshot resulted in: ${x.getClass.toString} ${x.getMessage}"
        )
        None
    }
  }

  /** takes a screenshot of the given element
    *
    * @param element
    *   the element to take a screenshot of
    * @return
    *   an option for a buffered image (none if retrieving screenshot failed)
    */
  def takeElementScreenshot(element: WebElement): Option[BufferedImage] = {
    try {
      val bytes: Array[Byte] = Base64.getDecoder.decode(
        element
          .getScreenshotAs(OutputType.BASE64)
          .replaceAll("\n", "")
      )
      val inputStream = new ByteArrayInputStream(bytes)
      val image: BufferedImage = ImageIO.read(inputStream)
      Some(image)
    } catch {
      case _: ScreenshotException =>
        error("cannot take screenshot")
        None
      case x: Throwable =>
        error(
          s"taking screenshot resulted in: ${x.getClass.toString} ${x.getMessage}"
        )
        None
    }
  }

  def setClipboardContent(content: String): Unit = {
    info(s"set clipboard content $content")
    driver.get.asInstanceOf[HasClipboard].setClipboardText(content)
  }

}

object Appium extends LogSupport {

  /** Starts appium for the current phone
    *
    * IMPORTANT: You need to start the app AFTER appium as starting appium might
    * trigger a restart on Android if it fails to start
    *
    * @param conf
    *   the current config
    * @param func
    *   the function to execute with appium active
    * @tparam T
    *   the return type
    * @return
    *   the return value of the function func
    */
  def withRunningAppium[T](appId: String, conf: Config, device: Device)(
      func: Appium => T
  ): T = {
    val appium: Appium = device.PLATFORM_OS match {
      case PlatformOperatingSystems.ANDROID =>
        if (conf.android.appium) {
          new AndroidAppium(conf)
        } else {
          new NoAppium()
        }
      case appanalyzer.platform.PlatformOperatingSystems.IOS =>
        if (conf.ios.appium) {
          new iOSAppium(conf)
        } else {
          new NoAppium()
        }
    }
    try {
      try {
        appium.startAppiumServer(conf.appium)
        appium.connect(appId)
      } catch {
        case x: Throwable =>
          error(s"encountered appium start error:\n ${x.getMessage}")
          device.PLATFORM_OS match {
            case appanalyzer.platform.PlatformOperatingSystems.ANDROID =>
              info("restarting the device and performing reconnect")
              appium.stopAppiumServer()
              device.restartPhone()
              try {
                appium.startAppiumServer(conf.appium)
                appium.connect(appId)
              } catch {
                case x: Throwable =>
                  error(s"restart did not help:\n ${x.getMessage}")
                  throw new FatalError("Appium did not start successfully")
              }
            case appanalyzer.platform.PlatformOperatingSystems.IOS =>
              throw new FatalError("Appium did not start successfully")
          }
      }
      func(appium)
    } finally {
      appium.stop()
      appium.stopAppiumServer()
    }
  }

}
