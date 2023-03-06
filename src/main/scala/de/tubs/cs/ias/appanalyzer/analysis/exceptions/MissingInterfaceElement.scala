package de.tubs.cs.ias.appanalyzer.analysis.exceptions

case class MissingInterfaceElement(element: String, interface: String)
    extends InterfaceAnalysisCondition {

  override val message: String =
    s"expected element $element in $interface but did not find it"

}
