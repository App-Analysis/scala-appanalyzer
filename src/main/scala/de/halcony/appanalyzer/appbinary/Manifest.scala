package de.halcony.appanalyzer.appbinary

import spray.json.{DefaultJsonProtocol, RootJsonFormat}


case class Manifest(filePath: String, apps: List[String])

object ManifestJsonProtocol extends DefaultJsonProtocol {
  // Define the implicit format for AppManifest
  implicit val ManifestFormat: RootJsonFormat[Manifest] = jsonFormat2(Manifest)
}