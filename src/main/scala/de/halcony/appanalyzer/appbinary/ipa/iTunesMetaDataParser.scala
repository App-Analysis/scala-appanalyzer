package de.halcony.appanalyzer.appbinary.ipa

import scala.xml.Elem

object iTunesMetaDataParser {

  /** parse the key from the given XML element
  *
  * @param xml the XML element expected to have the label "key"
  * @return the text content of the key element
  */
  def parseKey(xml: Elem): String = {
    assert(xml.label == "key")
    xml.text
  }

  /** parse a dictionary from the given XML element
  *
  * @param xml the XML element expected to have the label "dict"
  * @return a MetaDict representing the parsed dictionary
  */
  private def parseDict(xml: Elem): MetaDict = {
    assert(xml.label == "dict")
    val map = xml.child
      .grouped(2)
      .map { pair =>
        (parseKey(pair.head.asInstanceOf[Elem]) -> parseElement(
          pair(1).asInstanceOf[Elem]
        ))
      }
      .toMap
    MetaDict(map)
  }

  /** parse an integer from the given XML element
  *
  * @param xml the XML element expected to have the label "integer"
  * @return a MetaInteger representing the parsed integer value
  */
  private def parseInteger(xml: Elem): MetaInteger = {
    assert(xml.label == "integer")
    MetaInteger(xml.text.toInt)
  }

  /** parse a string from the given XML element
  *
  * @param xml the XML element expected to have the label "string"
  * @return a MetaString representing the parsed string value
  */
  private def parseString(xml: Elem): MetaString = {
    assert(xml.label == "string")
    MetaString(xml.text)
  }

  /** parse a boolean value from the given XML element
  *
  * @param xml the XML element expected to have the label "true" or "false"
  * @return a MetaBoolean representing the parsed boolean value
  */
  private def parseBoolean(xml: Elem): MetaBoolean = {
    assert(xml.label == "true" || xml.label == "false")
    val value = xml.label match {
      case "true"  => true
      case "false" => false
    }
    MetaBoolean(value)
  }

  /** parse an unknown XML element into a MetaUnknown
  *
  * @param elem the XML element with an unrecognized label
  * @return a MetaUnknown containing the element's label and text content
  */
  private def parseUnknown(elem: Elem): MetaUnknown = {
    MetaUnknown(elem.label, elem.text)
  }

  /** parse the given XML element into an iTunesMetaDataElement
  *
  * @param xml the XML element to parse
  * @return an iTunesMetaDataElement representing the parsed content of the XML element
  */
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
