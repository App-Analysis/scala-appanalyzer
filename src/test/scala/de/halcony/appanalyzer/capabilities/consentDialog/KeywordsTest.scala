package de.halcony.appanalyzer.capabilities.consentDialog

import de.halcony.appanalyzer.analysis.actors.consent.Keywords
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class KeywordsTest extends AnyWordSpec with Matchers {

  val keywords : Keywords = Keywords.read("./resources/consent/indicators.json")

  "clearAffirmativeButtons" should {
    "match with test text" in {
      val testTexts : List[(String,Int)] = List(
        ("accept and continue",0),("consent and continue",0),
        ("accept",1),
        ("select all",6),
        ("akzeptiere und weiter",8),("akzeptieren und weiter",8),("genehmige und weiter",8),
        ("akzeptiere",9),("akzeptieren",9),
        ("alle wählen",16),("alle auswählen",16),
        ("stimme zu",17),
        ("nehme an",18),
        ("willig ein",19)
      )
      val regex = keywords.getButtonClearAffirmativeKeywords
      testTexts.foreach {
        case (string,regexIndex) =>
          regex.indexWhere(_.findFirstMatchIn(string).nonEmpty) shouldBe regexIndex
      }
    }
  }
  "dialog" should {
    "match with test text" in {
      val testTexts: List[(String, Int)] = List(
        ("agree to our terms of service and acknowledge that you have read our privacy policy to learn how we collect",7)
      )
      val regex = keywords.getDialogKeywords
      testTexts.foreach {
        case (string, regexIndex) =>
          regex.indexWhere(_.findFirstMatchIn(string).nonEmpty) shouldBe regexIndex
      }
    }
  }

}
