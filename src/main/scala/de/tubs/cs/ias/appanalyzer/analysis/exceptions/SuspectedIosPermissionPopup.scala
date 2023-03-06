package de.tubs.cs.ias.appanalyzer.analysis.exceptions

import de.tubs.cs.ias.appanalyzer.platform.exceptions.FatalError

class SuspectedIosPermissionPopup(popupMsg: String,
                                  comment: String = "weird stuff happened")
    extends FatalError(comment) {
  override val msg =
    s"we suspect to have detected a permission popup $popupMsg but $comment"
}
