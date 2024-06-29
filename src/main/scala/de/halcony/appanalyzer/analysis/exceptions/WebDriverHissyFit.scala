package de.halcony.appanalyzer.analysis.exceptions

import org.openqa.selenium.WebDriverException

class WebDriverHissyFit(x: WebDriverException)
    extends InterfaceAnalysisCondition {

  override def getStacktraceString: String =
    x.getStackTrace.mkString("\n") // as this exception is only a wrapper

  override val message: String =
    s"The webdriver threw a hissy fit: ${x.getMessage}"

}
