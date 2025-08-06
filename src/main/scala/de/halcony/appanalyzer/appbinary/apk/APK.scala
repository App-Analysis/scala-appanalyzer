package de.halcony.appanalyzer.appbinary.apk

import de.halcony.appanalyzer.Config
import de.halcony.appanalyzer.appbinary.{Analysis, MobileApp}
import wvlet.log.LogSupport

import scala.annotation.nowarn
import scala.collection.mutable.ListBuffer
import scala.sys.process._

case class APK(conf: Config) extends Analysis with LogSupport {

  /** retrieve the list of included files in the APK by running dexdump
    *
    * @param path
    *   the path to the APK file
    * @return
    *   a list of class descriptors found in the APK
    */
  override def getIncludedFiles(path: String): List[String] = {
    val dexdump = conf.android.dexdump
    val stdout = ListBuffer[String]()
    s"$dexdump $path" ! ProcessLogger(fout => stdout.append(fout), _ => ())
    stdout
      .filter(_.contains("Class descriptor"))
      .map(line => line.split(':').last.trim.replace("'", "").replace(";", ""))
      .toList
  }

  /** perform cleanup operations
    *
    * No cleanup actions are performed in this implementation.
    */
  override def cleanUp(): Unit = {}

  /** retrieve the application version from the APK manifest
    *
    * @param path
    *   the path to the APK file
    * @return
    *   the version code extracted from the APK
    */
  override def getAppVersion(path: String): String = {
    ApkAnalyzer(conf.android.apkanalyzer)
      .getManifestAttribute(path, ManifestAttributes.VERSION_CODE)
  }

  /** retrieve the application ID from the APK manifest
    *
    * @param path
    *   the path to the APK file
    * @return
    *   the application ID extracted from the APK
    */
  override def getAppId(path: String): String = {
    ApkAnalyzer(conf.android.apkanalyzer)
      .getManifestAttribute(path, ManifestAttributes.APPLICATION_ID)
  }

  /** retrieve the application ID from a MobileApp instance by analyzing its APK
    * file
    *
    * @param app
    *   the MobileApp instance containing the APK path
    * @param default
    *   an optional default value (ignored in this implementation)
    * @return
    *   the application ID extracted from the APK
    */
  override def getAppId(
      app: MobileApp,
      @nowarn default: Option[String]
  ): String = {
    getAppId(app.escaped_path)
  }
}
