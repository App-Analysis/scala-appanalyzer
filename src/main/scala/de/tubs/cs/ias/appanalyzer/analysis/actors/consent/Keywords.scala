package de.tubs.cs.ias.appanalyzer.analysis.actors.consent

import spray.json.{DefaultJsonProtocol, JsonParser, RootJsonFormat}

import scala.io.Source
import scala.util.matching.Regex

case class ButtonKeywords(clearAffirmative: List[String],
                          hiddenAffirmative: List[String],
                          clearNegative: List[String],
                          hiddenNegative: List[String])

case class Keywords(clearNegativeWords: List[String],
                    negators: List[String],
                    dialog: List[String],
                    link: List[String],
                    regularKeywords: List[String],
                    halfKeywords: List[String],
                    buttons: ButtonKeywords) {

  def convertToRegexp(strings: List[String],
                      enforceBoundaries: Boolean): List[Regex] = {
    strings
      .map { str =>
        if (enforceBoundaries) s"\\b${str.toLowerCase}\\b"
        else str.toLowerCase
      }
      .flatMap { str =>
        if (str.contains("_")) {
          List(str.r, str.replace("_", "").r)
        } else {
          List(str.r)
        }
      }
  }

  def getNegatorKeywords: List[Regex] =
    convertToRegexp(negators, enforceBoundaries = true)

  def getDialogKeywords: List[Regex] =
    convertToRegexp(dialog, enforceBoundaries = false)
  def getLinkKeywords: List[Regex] =
    convertToRegexp(link, enforceBoundaries = true)
  def getRegularKeywords: List[Regex] =
    convertToRegexp(regularKeywords, enforceBoundaries = false)
  def getHalfKeywords: List[Regex] =
    convertToRegexp(halfKeywords, enforceBoundaries = false)
  def getButtonClearAffirmativeKeywords: List[Regex] =
    convertToRegexp(buttons.clearAffirmative, enforceBoundaries = true)
  def getButtonHiddenAffirmativeKeywords: List[Regex] =
    convertToRegexp(buttons.hiddenAffirmative, enforceBoundaries = true)
  def getButtonClearNegativeKeywords: List[Regex] =
    convertToRegexp(clearNegativeWords ++ buttons.clearNegative,
                    enforceBoundaries = true)
  def getButtonHiddenNegativeKeywords: List[Regex] =
    convertToRegexp(buttons.hiddenNegative, enforceBoundaries = true)
  def getButtonNegatorKeywords: List[Regex] =
    convertToRegexp(negators ++ clearNegativeWords, enforceBoundaries = true)

}

object Keywords extends DefaultJsonProtocol {

  implicit val buttonKeywordsFormat: RootJsonFormat[ButtonKeywords] =
    jsonFormat4(ButtonKeywords)
  implicit val keywordsFormat: RootJsonFormat[Keywords] = jsonFormat7(
    Keywords.apply)

  def read(path: String): Keywords = {
    val source = Source.fromFile(path)
    try {
      JsonParser(source.getLines().mkString("\n")).convertTo[Keywords]
    } finally {
      source.close()
    }
  }

}
