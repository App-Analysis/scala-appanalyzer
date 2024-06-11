package de.halcony.appanalyzer.platform.exceptions

import de.halcony.appanalyzer.appbinary.MobileApp

/** this error represents an issue uninstalling an app
  *
  * @param app
  *   the app that cannot be uninstalled
  * @param msg
  *   any corresponding custom message
  */
case class UnableToUninstallApp(
    app: MobileApp,
    msg: String = "unexpected issue"
) extends Throwable {

  override def getMessage: String = s"unable to uninstall app $app due to $msg"

}
