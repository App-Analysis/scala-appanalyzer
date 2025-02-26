package de.halcony.appanalyzer.parser

import de.halcony.argparse.Parser

/** The main parser for the AppAnalyzer Creates the PluginManagerParser,
  * DeletedAnalysisParser and RunParser. Structure: an object without
  * parenthesis with a method createParser method Sub-parsers are thematically
  * new objects or new functions.
  * @return
  *   the main parser
  */
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
