package de.halcony.appanalyzer.analysis.plugin

case class RemotePluginConfig(owner: String, repo: String)

case class PluginManagerConfiguration(
    folder: String,
    available: Map[String, RemotePluginConfig]
)
