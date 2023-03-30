package de.halcony.appanalyzer.platform.exceptions

import de.halcony.appanalyzer.appbinary.MobileApp

/** condition that an app cannot be installed
  *
  * @param app the app that cannot be installed
  * @param msg any corresponding message
  */
case class UnableToInstallApp(app: MobileApp, msg: String = "unexpected issue")
    extends Throwable {

  override def getMessage: String = s"unable to install app $app due to $msg"

}
