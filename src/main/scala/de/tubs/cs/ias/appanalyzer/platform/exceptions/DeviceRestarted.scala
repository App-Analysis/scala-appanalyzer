package de.tubs.cs.ias.appanalyzer.platform.exceptions

/** condition that can be thrown if a device is restarted
  *
  * CURRENTLY UNUSED
  *
  */
object DeviceRestarted extends Throwable {

  override def getMessage: String =
    "had to restart device ... this is an indicator for a bad issue"

}
