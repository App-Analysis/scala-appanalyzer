package de.tubs.cs.ias.appanalyzer.analysis.exceptions

case class UnexpectedInterface(msg: String) extends InterfaceAnalysisCondition {
  override val message: String = msg
}
