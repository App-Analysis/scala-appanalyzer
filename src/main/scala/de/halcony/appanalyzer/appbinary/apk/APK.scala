package de.halcony.appanalyzer.appbinary.apk

import de.halcony.appanalyzer.Config
import de.halcony.appanalyzer.appbinary.{Analysis, MobileApp}
import wvlet.log.LogSupport
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

  override def getAppId(app: MobileApp): String = {
    val out = new ListBuffer[String]()
    val err = new ListBuffer[String]()
    val cmd = s"${conf.android.apkanalyzer} manifest application-id ${app.path}"
    val ret =
      cmd ! ProcessLogger(line => out.append(line), line => err.append(line))
    if (ret != 0 || err.nonEmpty) {
      throw new RuntimeException("cannot read manifest file application-id")
    } else {
      out.mkString("\n").trim
    }
  }

}
