import de.halcony.appanalyzer.{Config, Mailer}
import de.halcony.appanalyzer.analysis.plugin.RemotePluginConfig
import org.scalatest._
import org.scalatest.flatspec._
import org.scalatest.matchers._

class TestMailer extends AnyFlatSpec with should.Matchers {
  "Mailer" should "send email" in {
    val conf = Config.parse("config.json")
    val mailer = new Mailer(conf.email.get)
    mailer.send_email(subject = "Test", content = "Test")
  }

  "Mailer" should "be mappable" in {
    val conf = Config.parse("config.json")
    val mailer: Option[Mailer] = conf.email.map(new Mailer(_))
    mailer match {
        case Some(mailer) => print(mailer)
        case None => print("email is optional, in this case None")
    }
  }
}
