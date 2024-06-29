package de.halcony.appanalyzer.platform.exceptions

/** exception when an app cannot be started
  *
  * @param appId
  *   the appid that cannot be started
  * @param msg
  *   any corresponding message
  */
case class UnableToStartApp(appId: String, msg: String = "unexpected issue")
    extends Throwable {

  override def getMessage: String = s"Unable to start app $appId due to $msg"

}
