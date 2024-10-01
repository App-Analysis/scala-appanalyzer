package de.halcony.appanalyzer.platform

/** enum representing the two different mobile OS we support
  */
object PlatformOperatingSystems extends Enumeration {
  type PlatformOS = Value
  val ANDROID, IOS = Value
}
