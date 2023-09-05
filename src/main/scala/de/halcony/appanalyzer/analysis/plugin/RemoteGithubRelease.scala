package de.halcony.appanalyzer.analysis.plugin

import spray.json.{JsArray, JsObject, JsString}

import scala.math.Ordered.orderingToOrdered

trait RemoteGithubPluginException extends Exception
object NoPluginJarDetected extends RemoteGithubPluginException
object NoValidSemVersion extends RemoteGithubPluginException

case class NoSuchPlugin(name: String, version: Option[String])
    extends RemoteGithubPluginException {
  override def getMessage: String =
    s"no plugin $name ${version.getOrElse("")} found"
}

case class RemoteGithubRelease(json: JsObject)
    extends Ordered[RemoteGithubRelease] {

  val jarDownloadLink: String = json
    .fields("assets")
    .asInstanceOf[JsArray]
    .elements
    .find { asset =>
      val download =
        asset.asJsObject.fields("browser_download_url").asInstanceOf[JsString]
      download.value.endsWith(".jar")
    }
    .getOrElse(throw NoPluginJarDetected)
    .asJsObject
    .fields("browser_download_url")
    .asInstanceOf[JsString]
    .value

  val major :: minor :: revision :: Nil =
    try {
      json
        .fields("name")
        .asInstanceOf[JsString]
        .value
        .substring(1)
        .split("\\.")
        .toList
    } catch {
      case _: Exception => throw NoValidSemVersion
    }

  def getVersion = s"$major.$minor.$revision"

  override def compare(that: RemoteGithubRelease): Int = {
    (major, minor, revision) compare (that.major, that.minor, that.revision)
  }
}
