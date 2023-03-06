package de.tubs.cs.ias.appanalyzer.platform.exceptions

/** thrown if something fatal and unrecoverable happend
  *
  * this condition MUST NOT BE SWALLOWED. At least rethrow it.
  *
  * @param msg the corresponding message
  */
class FatalError(val msg: String) extends Throwable {

  override def getMessage: String = s"FATAL: $msg"

}
