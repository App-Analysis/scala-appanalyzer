package de.halcony.appanalyzer.platform

/** enum representing the two different mobile OS we support
  */
object PlatformOS extends Enumeration {
  type PlatformOS = Value
  val Android, iOS = Value
}
