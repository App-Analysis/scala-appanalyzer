package de.halcony.appanalyzer.appbinary

import spray.json.{
  DefaultJsonProtocol,
  DeserializationException,
  JsArray,
  JsObject,
  JsString,
  JsValue,
  JsonFormat,
  RootJsonFormat,
  enrichAny
}
import de.halcony.appanalyzer.platform.PlatformOperatingSystems.{
  ANDROID,
  IOS,
  PlatformOS
}

import scala.collection.mutable
import java.nio.file.Path

case class Manifest(appFolderPath: Path, apps: mutable.Set[MobileApp])

object ManifestJsonProtocol extends DefaultJsonProtocol {
  implicit def mutableSetFormat[T: JsonFormat]: JsonFormat[mutable.Set[T]] =
    new JsonFormat[mutable.Set[T]] {
      override def write(set: mutable.Set[T]): JsValue = JsArray(
        set.toSeq.map(_.toJson): _*
      )
      def read(json: JsValue): mutable.Set[T] = json match {
        case JsArray(elements) =>
          mutable.Set(elements.map(_.convertTo[T]): _*)
        case _ =>
          throw DeserializationException("Expected JsArray")
      }
    }

  implicit val pathFormat: RootJsonFormat[Path] = new RootJsonFormat[Path] {
    override def read(json: JsValue): Path = json match {
      case JsString(path) => Path.of(path)
      case _              => throw DeserializationException("Path expected")
    }
    override def write(obj: Path): JsValue = JsString(obj.toString)
  }

  implicit val platformOSFormat: RootJsonFormat[PlatformOS] =
    new RootJsonFormat[PlatformOS] {
      override def write(os: PlatformOS): JsString = os match {
        case ANDROID => JsString("android")
        case IOS     => JsString("ios")
      }
      override def read(json: JsValue): PlatformOS = json match {
        case JsString("android") => ANDROID
        case JsString("ios")     => IOS
        case os =>
          throw DeserializationException(s"Unknown OS: $os")
      }
    }

  implicit val mobileAppFormat: RootJsonFormat[MobileApp] =
    new RootJsonFormat[MobileApp] {
      override def write(obj: MobileApp): JsValue = {
        JsObject(
          "id" -> JsString(obj.id),
          "version" -> JsString(obj.version),
          "os" -> obj.os.toJson,
          "path" -> obj.path.toJson
        )
      }
      override def read(json: JsValue): MobileApp =
        json.asJsObject.getFields("id", "version", "os", "path") match {
          case Seq(JsString(id), JsString(version), osJson, pathJson) =>
            val os = osJson.convertTo[PlatformOS]
            val path = pathJson.convertTo[Path]
            MobileApp(id, version, os, path)
          case _ => throw DeserializationException("MobileApp expected")
        }
    }

  implicit val ManifestFormat: RootJsonFormat[Manifest] =
    new RootJsonFormat[Manifest] {
      override def read(json: JsValue): Manifest =
        json.asJsObject.getFields("appFolderPath", "apps") match {
          case Seq(JsString(appFolderPath), appsJson) =>
            val apps = appsJson.convertTo[mutable.Set[MobileApp]]
            Manifest(Path.of(appFolderPath), apps)
          case _ => throw DeserializationException("AppManifest expected")
        }

      override def write(obj: Manifest): JsValue = JsObject(
        "appFolderPath" -> JsString(obj.appFolderPath.toString),
        "apps" -> obj.apps.toJson
      )
    }

  implicit val AppManifestFormat: RootJsonFormat[AppManifest] =
    new RootJsonFormat[AppManifest] {
      override def read(json: JsValue): AppManifest =
        json.asJsObject.getFields("appFolderPath", "apps") match {
          case Seq(JsString(appFolderPath), appsJson) =>
            val apps = appsJson.convertTo[mutable.Set[MobileApp]]
            AppManifest(Path.of(appFolderPath), apps)
          case _ => throw DeserializationException("AppManifest expected")
        }

      override def write(obj: AppManifest): JsValue = JsObject(
        "appFolderPath" -> JsString(obj.appFolderPath.toString),
        "apps" -> obj.apps.toJson
      )
    }
}
