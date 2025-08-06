package de.halcony.appanalyzer.appbinary.ipa

import de.halcony.appanalyzer.Config
import de.halcony.appanalyzer.appbinary.{Analysis, MobileApp}
import de.halcony.appanalyzer.platform.exceptions.FatalError
import wvlet.log.LogSupport

import java.io.File
import java.util.zip.ZipFile
import scala.xml.Elem
import scala.language.reflectiveCalls
import scala.sys.process._

case class IPA(conf: Config) extends Analysis with LogSupport {

  /** retrieve the list of files included in the IPA archive using zipinfo
    *
    * @param path
    *   the path to the IPA file
    * @return
    *   a list of file paths contained in the IPA archive
    */
  override def getIncludedFiles(path: String): List[String] = {
    val zipinfo =
      "zipinfo" // for now we are assuming this is installed - if this bites you later on ... well ...
    val data = s"$zipinfo -l $path".!!
    data
      .split("\n")
      .map { line =>
        line.split(' ').last
      }
      .toList
  }

  /** execute a block of code with a resource that will be automatically closed
    *
    * @param resource
    *   the resource to be used and closed
    * @param block
    *   the block of code to execute with the resource
    * @return
    *   the result of the block execution
    */
  private def using[T <: { def close(): Unit }, U](
      resource: T
  )(block: T => U): U = {
    try {
      block(resource)
    } finally {
      if (resource != null) {
        resource.close()
      }
    }
  }

  /** perform cleanup operations
    *
    * No cleanup actions are performed in this case
    */
  override def cleanUp(): Unit = {}

  /** retrieve the application ID from the IPA's iTunesMetadata.plist
    *
    * @param app
    *   the MobileApp instance containing the IPA file path
    * @param default
    *   an optional default value to return if metadata is missing
    * @return
    *   the application ID extracted from the IPA metadata
    * @throws FatalError
    *   if the metadata is missing and no default value is provided
    */
  override def getAppId(app: MobileApp, default: Option[String]): String = {
    using(new ZipFile(new File(app.escaped_path))) { zipFile =>
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
          warn(s"file ${app.escaped_path} does not have an iTunesMetaData.plist")
          default.getOrElse(
            throw new FatalError(
              s"file ${app.escaped_path} does not have an iTunesMetaData.plist"
            )
          )
      }
    }
  }

  /** retrieve the application ID from the IPA file using its path
    *
    * This method is not implemented
    *
    * @param path
    *   the path to the IPA file
    * @return
    *   always throws NotImplementedError
    */
  override def getAppId(path: String): String = throw new NotImplementedError()

  /** retrieve the application version from the IPA file
    *
    * This method is not implemented
    *
    * @param path
    *   the path to the IPA file
    * @return
    *   always throws NotImplementedError
    */
  override def getAppVersion(path: String): String =
    throw new NotImplementedError()
}
