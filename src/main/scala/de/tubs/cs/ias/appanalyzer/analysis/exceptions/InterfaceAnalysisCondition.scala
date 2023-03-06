package de.tubs.cs.ias.appanalyzer.analysis.exceptions

trait InterfaceAnalysisCondition extends Throwable {

  val message: String

  override def getMessage: String = message

  def getStacktraceString: String = this.getStackTrace.mkString("\n")

}
