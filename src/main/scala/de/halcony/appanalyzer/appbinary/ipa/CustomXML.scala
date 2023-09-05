package de.halcony.appanalyzer.appbinary.ipa

import javax.xml.parsers.SAXParserFactory
import scala.xml.factory.XMLLoader
import scala.xml.{Elem, SAXParser}

object CustomXML extends XMLLoader[Elem] {
  override def parser: SAXParser = {
    val factory = SAXParserFactory.newInstance()
    factory.setFeature(
      "http://apache.org/xml/features/disallow-doctype-decl",
      false
    )
    factory.newSAXParser()
  }
}
