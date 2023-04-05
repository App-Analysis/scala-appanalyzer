package de.halcony.appanalyzer.platform.exceptions

/** condition thrown if an app closed itself
  *
  * @param appId the id of app that did close itself
  */
case class AppClosedItself(appId: String) extends Throwable {
  override def getMessage: String = s"$appId closed itself"

}
