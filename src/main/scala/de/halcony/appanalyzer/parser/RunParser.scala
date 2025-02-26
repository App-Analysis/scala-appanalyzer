package de.halcony.appanalyzer.parser

import de.halcony.appanalyzer.AppAnalyzer.{
  functionalityCheck,
  runPluginExperiment
}
import de.halcony.appanalyzer.Config
import de.halcony.appanalyzer.appbinary.AppManifest.updateOrCreateManifestMain
import de.halcony.argparse.{Parser, ParsingResult}

object RunParser {
  def createParser: Parser = {
    Parser(
      name = "run",
      description = "run an action/analysis"
    )
      .addPositional(
        name = "platform",
        description =
          "the platform to be analyzed [android_device,android_device_non_root,android_device_droidbot,android_emulator_root,ios]"
      )
      .addPositional(
        name = "path",
        description = "path to the required data for the chosen action"
      )
      .addOptional(
        name = "manifest",
        short = "m",
        description = "the path to the manifest to be used for this run"
      )
      .addOptional(
        name = "device",
        short = "d",
        description = "if provided specifies the devices in adb"
      )
      .addSubparser(createAppManifestParser)
      .addSubparser(createFunctionalityCheckParser)
      .addSubparser(createPluginParser)
  }

  private def createFunctionalityCheckParser: Parser = {
    Parser(
      name = "functionalityCheck",
      description =
        "run through all fundamental API actions to check if it works"
    ).addDefault[(ParsingResult, Config) => Unit]("func", functionalityCheck)
  }

  private def createPluginParser: Parser = {
    Parser(
      name = "plugin",
      description = "run an analysis using a plugin"
    )
      .addPositional(
        name = "plugin",
        description =
          "the name of the actor plugin providing the analysis capabilities"
      )
      .addFlag(
        name = "ephemeral",
        short = "e",
        description =
          "if set the experiment will be deleted directly after execution"
      )
      .addFlag(
        name = "empty",
        short = "w",
        description =
          "if set then no app is installed and the analysis is run on the raw OS"
      )
      .addFlag(
        name = "no-app-start-check",
        short = "n",
        description = "if set there is no check if the app is running"
      )
      .addOptional(
        name = "only",
        short = "o",
        long = "only",
        default = None,
        description =
          "a file or a csv list listing app ids that shall be analyzed (any other app is ignored)"
      )
      .addOptional(
        name = "description",
        short = "d",
        long = "description",
        default = Some(""),
        description = "an optional experiment description"
      )
      .addOptional(
        name = "batchSize",
        short = "b",
        long = "batchSize",
        default = None,
        description = "limit the amount of apps that are analyzed in bulk"
      )
      .addOptional(
        name = "continue",
        short = "r",
        long = "resume",
        default = None,
        description = "provides the experiment to be continued"
      )
      .addOptional(
        name = "parameters",
        short = "p",
        long = "parameters",
        default = None,
        description = "a csv list of <key>=<value> pairs"
      )
      .addDefault[(ParsingResult, Config) => Unit](
        "func",
        runPluginExperiment,
        "runs an experiment using the specified plugin"
      )
  }

  private def createAppManifestParser: Parser = {
    Parser("manifest")
      .addSubparser(createUpdateParser)
  }

  private def createUpdateParser: Parser = {
    Parser(
      "update",
      "updates the manifest file for the provided app data collection"
    )
      .addDefault[(ParsingResult, Config) => Unit](
        "func",
        updateOrCreateManifestMain
      )
  }
}
