package de.halcony.appanalyzer.platform.frida

import de.halcony.appanalyzer.platform.PlatformOS
import PlatformOS.PlatformOS
import de.halcony

trait FridaScripts {
  def getPrefs: String

  def setClipboard(text: String): String
}

object FridaScripts {

  def platform(platform: PlatformOS): FridaScripts = platform match {
    case PlatformOS.Android | PlatformOS.AndroidEmulatorRoot =>
      AndroidFridaScripts
    case halcony.appanalyzer.platform.PlatformOS.iOS =>
      iOSFridaScripts
  }

}
