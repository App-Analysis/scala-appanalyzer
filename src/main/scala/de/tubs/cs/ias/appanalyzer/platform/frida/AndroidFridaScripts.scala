package de.tubs.cs.ias.appanalyzer.platform.frida

object AndroidFridaScripts extends FridaScripts {

  override def getPrefs: String =
    """var app_ctx = Java.use('android.app.ActivityThread').currentApplication().getApplicationContext();
      |var pref_mgr = Java.use('android.preference.PreferenceManager').getDefaultSharedPreferences(app_ctx);
      |var HashMapNode = Java.use('java.util.HashMap$Node');
      |
      |var prefs = {};
      |
      |var iterator = pref_mgr.getAll().entrySet().iterator();
      |while (iterator.hasNext()) {
      |    var entry = Java.cast(iterator.next(), HashMapNode);
      |    prefs[entry.getKey().toString()] = entry.getValue().toString();
      |}
      |
      |send({ name: "get_obj_from_frida_script", payload: prefs });
      |""".stripMargin

  override def setClipboard(text: String): String = {
    s"""var app_ctx = Java.use('android.app.ActivityThread').currentApplication().getApplicationContext();
       |var cm = Java.cast(app_ctx.getSystemService("clipboard"), Java.use("android.content.ClipboardManager"));
       |cm.setText(Java.use("java.lang.StringBuilder").$$new("$text"));
       |send({ name: "get_obj_from_frida_script", payload: true });
       |""".stripMargin
  }
}
