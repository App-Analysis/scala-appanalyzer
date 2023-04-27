package de.halcony.appanalyzer.appbinary.ipa

import scala.xml.Elem

object iTunesMetaDataParser {

  def parseKey(xml: Elem): String = {
    assert(xml.label == "key")
    xml.text
  }

  private def parseDict(xml: Elem): MetaDict = {
    assert(xml.label == "dict")
    val map = xml.child
      .grouped(2)
      .map { pair =>
        (parseKey(pair.head.asInstanceOf[Elem]) -> parseElement(
          pair(1).asInstanceOf[Elem]))
      }
      .toMap
    MetaDict(map)
  }

  private def parseInteger(xml: Elem): MetaInteger = {
    assert(xml.label == "integer")
    MetaInteger(xml.text.toInt)
  }

  private def parseString(xml: Elem): MetaString = {
    assert(xml.label == "string")
    MetaString(xml.text)
  }

  private def parseBoolean(xml: Elem): MetaBoolean = {
    assert(xml.label == "true" || xml.label == "false")
    val value = xml.label match {
      case "true"  => true
      case "false" => false
    }
    MetaBoolean(value)
  }

  private def parseUnknown(elem: Elem): MetaUnknown = {
    MetaUnknown(elem.label, elem.text)
  }

  def parseElement(xml: Elem): iTunesMetaDataElement = {
    xml.label match {
      case "dict"           => parseDict(xml)
      case "integer"        => parseInteger(xml)
      case "string"         => parseString(xml)
      case "true" | "false" => parseBoolean(xml)
      case _                => parseUnknown(xml)
    }
  }

}
