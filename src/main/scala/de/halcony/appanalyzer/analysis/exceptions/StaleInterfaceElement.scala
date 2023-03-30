package de.halcony.appanalyzer.analysis.exceptions

import org.openqa.selenium.StaleElementReferenceException

class StaleInterfaceElement(x: StaleElementReferenceException)
    extends InterfaceAnalysisCondition {

  override val message: String =
    s"stale element references, presumably appium crapped out or the app died: ${x.getMessage}"
}
