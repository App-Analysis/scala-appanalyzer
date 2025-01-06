package de.halcony.appanalyzer

import wvlet.log.LogSupport

import java.util.Properties
import javax.mail._
import javax.mail.internet._

class Mailer(email_config: Email) extends LogSupport {

  val port: String = email_config.port.toString
  val host: String = email_config.host

  val user: String = email_config.user
  val password: String = email_config.password
  val recipients: List[String] = email_config.recipients

  val properties = new Properties
  properties.put("mail.smtp.port", port)
  properties.setProperty("mail.host", host)

  properties.setProperty("mail.smtp.starttls.enable", "true")
  properties.setProperty("mail.transport.protocol", "smtp")
  properties.setProperty("mail.smtp.ssl.trust", host)
  properties.setProperty("mail.smtp.ssl.protocols", "TLSv1.2")

  // properties.setProperty("mail.user", user)
  // properties.setProperty("mail.password", password)
  properties.setProperty("mail.smtp.auth", "true")

  def send_email(subject: String, content: String): Unit = {
    val session = Session.getInstance(
      properties,
      new Authenticator() {
        override def getPasswordAuthentication: PasswordAuthentication = {
          new PasswordAuthentication(user, password)
        }
      }
    )
    val message = new MimeMessage(session)
    email_config.email match {
      case Some(email: String) =>
        message.setFrom(new InternetAddress(email, "AppAnalyzer"))
      case None => message.setFrom(new InternetAddress(user, "AppAnalyzer"))
    }
    message.setSubject(subject)
    message.setText(content)

    for (recipient <- recipients) {
      message.addRecipient(
        Message.RecipientType.TO,
        new InternetAddress(recipient)
      )
    }
    Transport.send(message)
  }

}
