package de.halcony.appanalyzer.appbinary.apk

import de.halcony.appanalyzer.Config
import de.halcony.appanalyzer.appbinary.{Analysis, MobileApp}
import wvlet.log.LogSupport

import scala.annotation.nowarn
import scala.collection.mutable.ListBuffer
import scala.sys.process._

case class APK(conf: Config) extends Analysis with LogSupport {

  override def getIncludedFiles(path: String): List[String] = {
    val dexdump = conf.android.dexdump
    val stdout = ListBuffer[String]()
    s"$dexdump $path" ! ProcessLogger(fout => stdout.append(fout), _ => ())
    stdout
      .filter(_.contains("Class descriptor"))
      .map(line => line.split(':').last.trim.replace("'", "").replace(";", ""))
      .toList
  }

  override def cleanUp(): Unit = {}

  override def getAppVersion(path: String): String = {
    ApkAnalyzer(conf.android.apkanalyzer)
      .getManifestAttribute(path, ManifestAttributes.VERSION_CODE)
  }

  override def getAppId(path: String): String = {
    ApkAnalyzer(conf.android.apkanalyzer)
      .getManifestAttribute(path, ManifestAttributes.APPLICATION_ID)
  }

  override def getAppId(
      app: MobileApp,
      @nowarn default: Option[String]
  ): String = {
    getAppId(app.escaped_path)
  }

}
