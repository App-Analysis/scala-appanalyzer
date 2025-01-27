package de.halcony.appanalyzer

import de.halcony.appanalyzer.AppAnalyzer.{
  deleteAnalysisMain,
  functionalityCheck,
  runPluginExperiment
}
import de.halcony.appanalyzer.analysis.plugin.PluginManager
import de.halcony.appanalyzer.appbinary.AppManifest
import de.halcony.argparse.{Parser, ParsingResult}

class AppAnalyzerParser {
  def createParser(): Parser = {
    createMainParser()
  }

  private def createMainParser(): Parser = {
    Parser(
      name = "AppAnalyzer",
      description = "run apps and analyze their consent dialogs"
    )
      .addFlag(
        name = "verbose",
        short = "v",
        description = "if set a stacktrace is provided with any fatal error"
      )
      .addOptional(
        name = "config",
        short = "c",
        default = Some("config.json"),
        description = "the configuration file"
      )
      .addSubparser(PluginManager.parser)
      .addSubparser(createRemovedAnalysisParser())
      .addSubparser(createRunParser())
  }

  private def createRemovedAnalysisParser(): Parser = {
    Parser(
      name = "removedAnalysis",
      description = "delete listed analysis ids"
    ).addPositional(
      name = "analysisIds",
      description = "csv list of ids or file containing list of ids"
    ).addDefault[(ParsingResult, Config) => Unit]("func", deleteAnalysisMain)
  }

  private def createRunParser(): Parser = {
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
      .addSubparser(AppManifest.parser)
      .addSubparser(createFunctionalityCheckParser())
      .addSubparser(createPluginParser())
  }

  private def createFunctionalityCheckParser(): Parser = {
    Parser(
      name = "functionalityCheck",
      description =
        "run through all fundamental API actions to check if it works"
    ).addDefault[(ParsingResult, Config) => Unit]("func", functionalityCheck)
  }

  private def createPluginParser(): Parser = {
    val pluginParser = Parser(
      name = "plugin",
      description = "run an analysis using a plugin"
    )

    pluginParser
      .addPositional(
        name = "plugin",
        description =
          "the name of the actor plugin providing the analysis capabilities"
      )

    addFlags(pluginParser)
    addOptions(pluginParser)

    pluginParser
      .addDefault[(ParsingResult, Config) => Unit](
        "func",
        runPluginExperiment,
        "runs an experiment using the specified plugin"
      )
  }

  private def addFlags(parser: Parser): Parser = {
    parser
      .addFlag(
        name = "ephemeral",
        short= "e",
        description = "if set the experiment will be deleted directly after execution"
      )
      .addFlag(
        name = "empty",
        short = "w",
        description = "if set then no app is installed and the analysis is run on the raw OS"
      )
      .addFlag(
        name = "no-app-start-check",
        short = "n",
        description = "if set there is no check if the app is running"
      )
  }

  private def addOptions(parser: Parser): Parser = {
    parser
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
  }
}
