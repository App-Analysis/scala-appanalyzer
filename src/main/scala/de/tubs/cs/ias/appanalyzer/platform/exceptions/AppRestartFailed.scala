package de.tubs.cs.ias.appanalyzer.platform.exceptions

/** thrown if an app cannot be restarted
  *
  * UNUSED
  *
  * @param appId the appid of the app that cannot be restarted
  */
case class AppRestartFailed(appId: String) extends Throwable {

  override def getMessage: String = s"Unable to restart app $appId"

}
