package de.tubs.cs.ias.appanalyzer.analysis.actors.consent

object ConsentDialogActions extends Enumeration {
  type ConsentDialogAction = Value
  val INITIALIZE, COLLECT_TRAFFIC, REJECT_CONSENT, BRANCH_DONE, ACCEPT_CONSENT =
    Value
}
