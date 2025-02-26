package de.halcony.appanalyzer.parser

import de.halcony.appanalyzer.AppAnalyzer.deleteAnalysisMain
import de.halcony.appanalyzer.Config
import de.halcony.argparse.{Parser, ParsingResult}

/** The parser for the DeleteAnalysis. Removes Analysis from the database.
  */
object DeletedAnalysisParser {
  def createParser: Parser = {
    Parser(
      name = "removedAnalysis",
      description = "delete listed analysis ids"
    ).addPositional(
      name = "analysisIds",
      description = "csv list of ids or file containing list of ids"
    ).addDefault[(ParsingResult, Config) => Unit]("func", deleteAnalysisMain)
  }
}
