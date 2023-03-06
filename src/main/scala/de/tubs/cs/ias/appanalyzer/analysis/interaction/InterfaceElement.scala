package de.tubs.cs.ias.appanalyzer.analysis.interaction

import org.openqa.selenium.WebElement

class InterfaceElement(element: WebElement) {

  private val text = element.getText
  val isDisplayed: Boolean = true // extracting this information takes too long with appium for some apps
  val isEnabled: Boolean = true // extracting this information takes too long with appium for some apps

  def getText: String = text

  def click(): Unit = element.click()

  def getUnderlyingElement: WebElement = element

}
