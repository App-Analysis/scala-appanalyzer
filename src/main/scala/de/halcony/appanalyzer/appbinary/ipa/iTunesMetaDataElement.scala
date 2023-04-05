package de.halcony.appanalyzer.appbinary.ipa

trait iTunesMetaDataElement
case class MetaString(value: String) extends iTunesMetaDataElement {
  def get: String = value
}
case class MetaInteger(value: Int) extends iTunesMetaDataElement {
  def get: Int = value
}
case class MetaDict(value: Map[String, iTunesMetaDataElement])
    extends iTunesMetaDataElement {
  def get(name: String): iTunesMetaDataElement = value(name)
}
case class MetaBoolean(value: Boolean) extends iTunesMetaDataElement {
  def get: Boolean = value
}
case class MetaUnknown(label: String, value: String)
    extends iTunesMetaDataElement {
  def get: (String, String) = (label, value)
}
