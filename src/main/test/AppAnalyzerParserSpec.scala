import de.halcony.appanalyzer.AppAnalyzerParser
import de.halcony.argparse.ParsingResult

import org.scalatest.flatspec._
import org.scalatest.matchers._
import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper


class AppAnalyzerParserSpec extends AnyFlatSpec {
  def parseArgs(args: Array[String]): ParsingResult = {
    val parser = new AppAnalyzerParser().createParser()
    parser.parse(args)
  }

  "AppAnalyzerParser" should "parse installed plugins" in {
    val args = Array("plugin", "list", "installed")
    val result = parseArgs(args)
    print(result)
    val args2 = Array("test")
    val result2 = parseArgs(args2)
    print(result2)
  }
}
