package de.halcony.appanalyzer.analysis.exceptions

case class UncaughtCondition(thr: Throwable)
    extends InterfaceAnalysisCondition {
  override val message: String = s"UNCAUGHT: ${thr.getMessage}"
}
