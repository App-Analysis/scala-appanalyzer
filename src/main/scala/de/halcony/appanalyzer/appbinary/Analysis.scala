package de.halcony.appanalyzer.appbinary

trait Analysis {

  def cleanUp(): Unit

  def getAppId(app: MobileApp): String

  def getIncludedFiles(path: String): List[String]

}
