package de.halcony.appanalyzer.platform.frida

import de.halcony.appanalyzer.platform.PlatformOperatingSystems
import PlatformOperatingSystems.PlatformOS
import de.halcony

trait FridaScripts {
  def getPrefs: String

  def setClipboard(text: String): String
}

object FridaScripts {

  def platform(platform: PlatformOS): FridaScripts = platform match {
    case PlatformOperatingSystems.ANDROID =>
      AndroidFridaScripts
    case halcony.appanalyzer.platform.PlatformOperatingSystems.IOS =>
      iOSFridaScripts
  }

}
