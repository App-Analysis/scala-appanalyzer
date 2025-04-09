package de.halcony.appanalyzer.appbinary.apk

import scala.collection.mutable.ListBuffer
import scala.sys.process.ProcessLogger
import scala.sys.process._

object ManifestAttributes extends Enumeration {
  type ManifestAttribute = Value
  val APPLICATION_ID, VERSION_NAME, VERSION_CODE, MIN_SDK, TARGET_SDK,
      PERMISSIONS, DEBUGGABLE = Value
}

case class ApkAnalyzer(apkAnalyzerPath: String) {

  /** retrieve a manifest attribute from the APK file using the apkAnalyzer tool
  *
  * @param apk the path to the APK file
  * @param attribute the manifest attribute to retrieve
  * @return the extracted manifest attribute value as a String
  */
  def getManifestAttribute(
      apk: String,
      attribute: ManifestAttributes.ManifestAttribute
  ): String = {
    val attributeName: String = attribute match {
      case de.halcony.appanalyzer.appbinary.apk.ManifestAttributes.APPLICATION_ID =>
        "application-id"
      case de.halcony.appanalyzer.appbinary.apk.ManifestAttributes.VERSION_NAME =>
        "version-name"
      case de.halcony.appanalyzer.appbinary.apk.ManifestAttributes.VERSION_CODE =>
        "version-code"
      case de.halcony.appanalyzer.appbinary.apk.ManifestAttributes.MIN_SDK =>
        "min-sdk"
      case de.halcony.appanalyzer.appbinary.apk.ManifestAttributes.TARGET_SDK =>
        "target-sdk"
      case de.halcony.appanalyzer.appbinary.apk.ManifestAttributes.PERMISSIONS =>
        "permissions"
      case de.halcony.appanalyzer.appbinary.apk.ManifestAttributes.DEBUGGABLE =>
        "debuggable"
    }
    val out = new ListBuffer[String]()
    val err = new ListBuffer[String]()
    val cmd = s"$apkAnalyzerPath manifest $attributeName $apk"
    val ret =
      cmd ! ProcessLogger(line => out.append(line), line => err.append(line))
    if (ret != 0 || err.nonEmpty)
      throw new RuntimeException(s"cannot read manifest file $attributeName")
    else
      out.mkString("\n").trim
  }

}
