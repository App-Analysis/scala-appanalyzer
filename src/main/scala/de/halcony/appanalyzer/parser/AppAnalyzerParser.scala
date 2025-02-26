package de.halcony.appanalyzer.parser

import de.halcony.appanalyzer.analysis.plugin.PluginManager
import de.halcony.argparse.Parser

object AppAnalyzerParser {
  def createParser: Parser = {
    createMainParser
  }

  private def createMainParser: Parser = {
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
      .addSubparser(PluginManagerParser.createParser)
      .addSubparser(DeletedAnalysisParser.createParser)
      .addSubparser(RunParser.createParser)
  }
}
