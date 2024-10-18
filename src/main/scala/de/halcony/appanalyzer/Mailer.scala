package de.halcony.appanalyzer

import wvlet.log.LogSupport

import java.util.Properties
import javax.mail._
import javax.mail.internet._

class Mailer(email_config: Email) extends LogSupport {

  val port: String = email_config.port
  val host: String = email_config.host

  val user: String = email_config.user
  val password: String = email_config.password

  val properties = new Properties
  properties.put("mail.smtp.port", port)
  properties.setProperty("mail.transport.protocol", "smtp")
  properties.setProperty("mail.smtp.starttls.enable", "true")
  properties.setProperty("mail.host", host)
  // properties.setProperty("mail.user", user)
  // properties.setProperty("mail.password", password)
  properties.setProperty("mail.smtp.auth", "true")

  def send_email(recipient: String, subject: String, content: String): Unit = {
    val session = Session.getInstance(properties, new Authenticator() {
      override def getPasswordAuthentication: PasswordAuthentication = {
        new PasswordAuthentication(user, password)
      }
    })
    val message = new MimeMessage(session)
    message.setFrom(new InternetAddress(user))
    message.addRecipient(Message.RecipientType.TO, new InternetAddress(recipient))
    message.setSubject(subject)
    message.setText(content)
    Transport.send(message)
  }

}
