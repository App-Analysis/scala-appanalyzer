package de.tubs.cs.ias.appanalyzer.platform.frida

object iOSFridaScripts extends FridaScripts {

  override def getPrefs: String =
    """// Taken from: https://codeshare.frida.re/@dki/ios-app-info/
      |function dictFromNSDictionary(nsDict) {
      |    var jsDict = {};
      |    var keys = nsDict.allKeys();
      |    var count = keys.count();
      |    for (var i = 0; i < count; i++) {
      |        var key = keys.objectAtIndex_(i);
      |        var value = nsDict.objectForKey_(key);
      |        jsDict[key.toString()] = value.toString();
      |    }
      |
      |    return jsDict;
      |}
      |var prefs = ObjC.classes.NSUserDefaults.alloc().init().dictionaryRepresentation();
      |send({ name: "get_obj_from_frida_script", payload: dictFromNSDictionary(prefs) });
      |""".stripMargin

  override def setClipboard(text: String): String =
    s"""ObjC.classes.UIPasteboard.generalPasteboard.setString_("$text")""".stripMargin

  def getIdfv: String =
    s"""var idfv = ObjC.classes.UIDevice.currentDevice().identifierForVendor().toString();
       |send({ name: "get_obj_from_frida_script", payload: idfv });""".stripMargin

  def setLocationPermission(appId: String): String =
    s"""ObjC.classes.CLLocationManager.setAuthorizationStatusByType_forBundleIdentifier_(4, "$appId");""".stripMargin
}
