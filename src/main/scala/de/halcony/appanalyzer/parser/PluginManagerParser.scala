package de.halcony.appanalyzer.parser

import de.halcony.appanalyzer.analysis.plugin.PluginManager.{
  HasPluginManagerConfiguration,
  availablePlugins,
  installPlugin,
  listPlugins
}
import de.halcony.argparse.{Parser, ParsingResult}

/** The parser for the PluginManager. Manages the installed plugins. Moved from
  * the PluginManager.
  */
object PluginManagerParser {
  def createParser: Parser = {
    Parser("plugin", "manage the installed plugins")
      .addSubparser(createListParser)
      .addSubparser(createInstallParser)
  }

  private def createListParser: Parser = {
    Parser("list", "list all installed plugins")
      .addSubparser(createInstalledParser)
      .addSubparser(createAvailableParser)
  }

  private def createInstalledParser: Parser = {
    Parser("installed", "list all installed plugins")
      .addDefault[
        (ParsingResult, HasPluginManagerConfiguration) => Unit
      ](
        "func",
        listPlugins,
        "list all installed plugins"
      )
  }

  private def createAvailableParser: Parser = {
    Parser("available", "list all available plugins")
      .addFlag("all", "a", "all", "if set list all available versions")
      .addOptional(
        "filter",
        "f",
        "filter",
        None,
        "a regexp to filter plugins"
      )
      .addDefault[
        (ParsingResult, HasPluginManagerConfiguration) => Unit
      ](
        "func",
        availablePlugins,
        "list all online available plugins"
      )
  }

  private def createInstallParser: Parser = {
    Parser("install", "install a plugin")
      .addFlag(
        "update",
        "u",
        "update",
        "if set updates the plugin to the most current version"
      )
      .addFlag(
        "force",
        "f",
        "force",
        "if set removes an already installed version of the plugin"
      )
      .addOptional(
        "version",
        "r",
        "revision",
        None,
        "if set will install (if available) the specified version of the plugin"
      )
      .addPositional("plugin", "the name of the plugin")
      .addDefault[(ParsingResult, HasPluginManagerConfiguration) => Unit](
        "func",
        installPlugin,
        "install/update a named plugin"
      )
  }
}
