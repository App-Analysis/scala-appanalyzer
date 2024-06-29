package de.halcony.appanalyzer.platform.exceptions

/** condition that is thrown if frida instance died or could not be started
  */
case class FridaDied() extends Throwable {

  override def getMessage: String = "frida died - this is unexpected"

}
