package de.tubs.cs.ias.appanalyzer.platform.frida

import de.tubs.cs.ias.appanalyzer
import de.tubs.cs.ias.appanalyzer.platform.PlatformOS
import de.tubs.cs.ias.appanalyzer.platform.PlatformOS.PlatformOS

trait FridaScripts {
  def getPrefs: String

  def setClipboard(text: String): String
}

object FridaScripts {

  def platform(platform: PlatformOS): FridaScripts = platform match {
    case PlatformOS.Android =>
      AndroidFridaScripts
    case appanalyzer.platform.PlatformOS.iOS =>
      iOSFridaScripts
  }

}
