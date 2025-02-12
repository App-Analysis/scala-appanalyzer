package de.halcony.appanalyzer.analysis.plugin

import spray.json.{JsArray, JsObject, JsString}

import scala.math.Ordered.orderingToOrdered

trait RemoteGithubPluginException extends Exception
object NoPluginJarDetected extends RemoteGithubPluginException
object NoValidSemVersion extends RemoteGithubPluginException

/** exception thrown when the specified plugin is not found
 *
 * @param name 
 *  the name of the plugin that was not found
 * @param version 
 *   an optional version string of the plugin
 */
case class NoSuchPlugin(name: String, version: Option[String])
    extends RemoteGithubPluginException {
  override def getMessage: String =
    s"no plugin $name ${version.getOrElse("")} found"
}

/**
 * Represents a GitHub release for a remote plugin
 *
 * @param json 
 *  a JSON object containing the GitHub release data
 */
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

  /** retrieves the semantic version of the release in the format "major.minor.revision".
   *
   * @return the version string
   */
  def getVersion = s"$major.$minor.$revision"

  /** compares this release to another release based on version numbers
   *
   * @param that 
   *  another RemoteGithubRelease to compare against
   * @return an integer indicating whether this release is less than, equal to, or greater than the other
   */
  override def compare(that: RemoteGithubRelease): Int = {
    (major, minor, revision) compare (that.major, that.minor, that.revision)
  }
}
