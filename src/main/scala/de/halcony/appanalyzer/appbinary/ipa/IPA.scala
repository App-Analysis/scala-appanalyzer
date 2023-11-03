package de.halcony.appanalyzer.appbinary.ipa

import de.halcony.appanalyzer.Config
import de.halcony.appanalyzer.appbinary.{Analysis, MobileApp}
import de.halcony.appanalyzer.platform.exceptions.FatalError
import wvlet.log.LogSupport

import java.io.File
import java.lang
import java.util.zip.ZipFile
import scala.xml.Elem
import scala.language.reflectiveCalls
import scala.sys.process._

case class IPA(conf: Config) extends Analysis with LogSupport {

  override def getIncludedFiles(path: String): List[String] = {
    val zipinfo = "zipinfo" //for now we are assuming this is installed - if this bites you later on ... well ...
    val data = s"$zipinfo -l $path".!!
    data
      .split("\n")
      .map { line =>
        line.split(' ').last
      }
      .toList
  }

  private def using[T <: { def close(): Unit }, U](resource: T)(
      block: T => U): U = {
    try {
      block(resource)
    } finally {
      if (resource != null) {
        resource.close()
      }
    }
  }

  override def cleanUp(): Unit = {}

  override def getAppId(app: MobileApp, default : Option[String]): String = {
    using(new ZipFile(new File(app.path))) { zipFile =>
      val metadata = Option(zipFile.getEntry("iTunesMetadata.plist"))
      metadata match {
        case Some(data) =>
          val xml: Elem = CustomXML.load(zipFile.getInputStream(data))
          assert(xml.child.length == 1)
          val res = iTunesMetaDataParser
            .parseElement(xml.child.head.asInstanceOf[Elem])
            .asInstanceOf[MetaDict]
          res.value("softwareVersionBundleId").asInstanceOf[MetaString].value
        case None =>
          warn(s"file ${app.path} does not have an iTunesMetaData.plist")
          default.getOrElse(throw new FatalError(s"file ${app.path} does not have an iTunesMetaData.plist"))
      }
    }
  }
}
