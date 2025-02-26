import de.halcony.appanalyzer.appbinary.AppManifest.sanityCheck
import de.halcony.appanalyzer.appbinary.ManifestJsonProtocol.{
  AppManifestFormat,
  ManifestFormat
}
import de.halcony.appanalyzer.appbinary.{AppManifest, Manifest, MobileApp}
import de.halcony.appanalyzer.platform.PlatformOperatingSystems
import org.scalatest.flatspec._
import org.scalatest.matchers.should.Matchers.{be, convertToAnyShouldWrapper}
import spray.json._

import java.nio.file.{Files, Path}
import scala.collection.mutable
import scala.io.Source
import scala.jdk.CollectionConverters.IteratorHasAsScala

class AppAnalyzerAppManifestSpec extends AnyFlatSpec {
  def getApps(path: Path): List[String] = {
    val stream = Files.list(path)
    try {
      // Filter the files
      stream
        .filter(Files.isRegularFile(_)) // Keep only regular files
        .filter(filePath =>
          filePath.getFileName.toString.endsWith(".json")
        ) // Check suffix
        .map(_.toString) // Convert Path to String
        .iterator() // Convert Stream[Path] to an Iterator
        .asScala // Convert to a Scala collection for compatibility
        .toList // Collect to List
    } finally {
      stream.close() // Ensure the stream is closed
    }
  }

  "Filepath" should "list files" in {
    val path = Path.of("./")
    // Use Files.list to get a stream of files in the directory
    val files = getApps(path)
    println(files)
    files.foreach(println)
  }

  it should "not detect non existing files" in {
    val path = Path.of("./non-existing")
    Files.exists(path) should be(false)
  }

  "Manifest" should "serialize and back" in {
    val mobileApp = MobileApp(
      "App1",
      "1.0",
      PlatformOperatingSystems.ANDROID,
      Path.of("/somewhere/on/device")
    )
    // Create an example AppManifest
    val manifest = new AppManifest(
      Path.of("./manifest.json"),
      Path.of("/path/to/file"),
      mutable.Set(
        mobileApp
      )
    )

    // Serialize AppManifest to JSON
    val json = manifest.toJson
    println(s"Serialized JSON: $json")

    // Deserialize JSON back to AppManifest
    val jsonString =
      """{"apps":[{"id":"App1","os":"android","path":"com.app1.MainActivity","version":"1.0"}],"appFolderPath":"/path/to/file"}"""
    val deserializedManifest = jsonString.parseJson.convertTo[AppManifest]
    println(s"Deserialized AppManifest: $deserializedManifest")
  }

  it should "fail to deserialize missing appFilePath" in {
    // Create an invalid JSON
    val jsonString = """{"apps":["App1","App2","App3","App4"]}"""

    // Try
    assertThrows[DeserializationException](
      jsonString.parseJson.convertTo[AppManifest]
    )
  }

  it should "write Manifest to file and serialize back" in {
    val mobileApp = MobileApp(
      "App1",
      "1.0",
      PlatformOperatingSystems.ANDROID,
      Path.of("/somewhere/on/device")
    )
    // Create an example AppManifest

    val manifestPath = Path.of("./test/manifest.json")
    val manifest = new AppManifest(
      manifestPath,
      Path.of("/path/to/file"),
      mutable.Set(
        mobileApp
      )
    )
    Path.of("./test").toFile.mkdirs()
    manifestPath.toFile.delete()
    manifest.writeManifestToFile()

    // Read the file
    val source = Source.fromFile(manifestPath.toString)

    info("reading in lines")
    var lines = ""
    try {
      lines = source.getLines().mkString
    } catch {
      case _: Throwable =>
    } finally {
      source.close()
    }
    println(lines.getClass)
    println(lines.parseJson.convertTo[Manifest])
  }

  it should "be mutable" in {
    val mobileApp = MobileApp(
      "App1",
      "1.0",
      PlatformOperatingSystems.ANDROID,
      Path.of("/somewhere/on/device")
    )
    // Create an example AppManifest
    val manifest = new AppManifest(
      Path.of("./manifest.json"),
      Path.of("/path/to/file"),
      mutable.Set(
        mobileApp
      )
    )
    val mobileApp2 = MobileApp(
      "App2",
      "1.0",
      PlatformOperatingSystems.ANDROID,
      Path.of("/somewhere/on/device")
    )
    manifest.apps.add(mobileApp2)

    manifest.apps should be(Set(mobileApp, mobileApp2))
  }

  it should "remove duplicates" in {
    val mobileApp = MobileApp(
      "App1",
      "1.0",
      PlatformOperatingSystems.ANDROID,
      Path.of("/somewhere/on/device")
    )
    // Create an example AppManifest
    val manifest = new AppManifest(
      Path.of("./manifest.json"),
      Path.of("/path/to/file"),
      mutable.Set(
        mobileApp
      )
    )
    val mobileApp2 = MobileApp(
      "App1",
      "1.0",
      PlatformOperatingSystems.ANDROID,
      Path.of("/somewhere/on/device")
    )
    manifest.apps.add(mobileApp2)
    manifest.apps.add(mobileApp2)
    manifest.apps should be(Set(mobileApp))
  }

  it should "pass the sanity check" in {
    val mobileApp = MobileApp(
      "App1",
      "1.0",
      PlatformOperatingSystems.ANDROID,
      Path.of("/somewhere/on/device")
    )
    // Create an example AppManifest
    val manifest = new AppManifest(
      Path.of("./manifest"),
      Path.of("/somewhere/on/"),
      mutable.Set(
        mobileApp
      )
    )
    sanityCheck(manifest)

    val mobileApp2 = MobileApp(
      "App2",
      "1.0",
      PlatformOperatingSystems.ANDROID,
      Path.of("/somewhere/else/on/device")
    )
    manifest.apps.add(mobileApp2)
    assertThrows[RuntimeException](sanityCheck(manifest))
  }

  it should "fail the sanity check if invalid" in {
    val mobileApp = MobileApp(
      "App1",
      "1.0",
      PlatformOperatingSystems.ANDROID,
      Path.of("/somewhere/on/device")
    )
    val mobileApp2 = MobileApp(
      "App2",
      "1.0",
      PlatformOperatingSystems.ANDROID,
      Path.of("/somewhere/else/on/device")
    )
    // Create an example AppManifest
    val manifest = new AppManifest(
      Path.of("./manifest"),
      Path.of("/somewhere/on/"),
      mutable.Set(
        mobileApp,
        mobileApp2
      )
    )

    assertThrows[RuntimeException](sanityCheck(manifest))
  }
}
