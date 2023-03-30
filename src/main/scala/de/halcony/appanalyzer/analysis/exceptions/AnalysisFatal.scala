package de.halcony.appanalyzer.analysis.exceptions

case class AnalysisFatal(msg: String) extends Throwable {

  override def getMessage: String = s"analysis fatal issue: $msg"

}
