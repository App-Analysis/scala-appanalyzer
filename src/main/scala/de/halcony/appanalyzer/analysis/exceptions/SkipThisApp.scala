package de.halcony.appanalyzer.analysis.exceptions

case class SkipThisApp(override val message: String)
    extends InterfaceAnalysisCondition
