# App Analzyer

The scala-appanalyzer is a tool for traffic collection for mobile applications
for iOS, Android and AVDs (Android Emulators). It allows for plugin import
to enable a wide range of functions.

## "global" requirements

| requirement                                                          | installation                 |
|----------------------------------------------------------------------|------------------------------|
| [python](https://www.python.org/)                                    | recommended: python3         |
| [objection](https://github.com/sensepost/objection)                  | ```pip install objection```  |
| [mitmproxy](https://mitmproxy.org/)                                  | ```pip install mitmproxy```  |
| [node & npm](https://nodejs.org/en)                                  | recommended: node 16 & npm 8 |
| [frida](https://frida.re/)                                           | ```npm i -g frida```         |
| [Appium](http://appium.io/docs/en/2.0/)                              | ```npm i -g appium```        |
| [OpenJDK](https://openjdk.org/)                                      | Java 17                      |
| [scala](https://www.scala-lang.org/)                                 | i.e. via sbt                 |
| [sbt](https://www.scala-sbt.org/download.html)                       | i.e. via Coursier            |
| [Postgres](https://www.postgresql.org/)                              | via install script           |
| [AndroidStudio with CLI-tools](https://developer.android.com/studio) | via tar                      |

## Info

You might have to install atob as well via ```npm i -g atob```.  
- configure appium
CommandLine tools can be installed in Android Studio under ```Settings -> SDK -> SDK Tools```.
## Android

### Installing frida server

- download the correct version of frida server
- push to phone
- chmod +x (adb root or su -c)
- verify running with ./frida-server &

## iOS

## AVD or emulator

If you want to use an Android Virtual Device (AVD) you will also need:

- [AndroidStudio emulator](https://developer.android.com/studio)
- [root on AVD](https://github.com/newbit1/rootAVD)
- [installing the ca certificate for mitm](https://docs.mitmproxy.org/stable/howto-install-system-trusted-ca-android/)
- UiAutomator2 (Appium)

It is recommended to add the cli-tools and emulator to your path configuration for easier use.
This is done by adding
```
export PATH="$PATH:~/Android/Sdk/platform-tools"
export PATH="$PATH:~/Android/Sdk/emulator"
export PATH="$PATH:~/Android/Sdk/cmdline-tools/latest/bin"
```
to either your ```.bashrc``` or ```.profile```.
Further follow the instructions from [rootAVD](https://github.com/newbit1/rootAVD) to gain access to root on your emulator.
From here you can follow the instructions to install [frida-server](#installing-frida-server) on your phone. 

## publishing the appanalyzer locally

If your plugins have trouble finding the scala-appanalyzer repository you can clone the
repository and publish it locally such that other sbt apps can find it. To do this run
```sbt publishLocal``` in the cloned scala-appanalyzer. Make sure your project name and
the published version line up with the imported one.

## building a plugin

To build a plugin you need a file called ```[root]/project/plugin.sbt```
and import the sbt native packager.

- ```addSbtPlugin("com.github.sbt"      % "sbt-native-packager"   % "1.9.16")```

You can now run ```sbt package```
to create a JAR-file under ```[root]/target/scala-[version]/```.

## installing a plugin

### install a locally built plugin

If you haven't compiled your package to a jar yet, please see [Building a plugin](#building-a-plugin).

To install a plugin create a plugin folder. Add to your config file under ```plugin```
the folder. You might have to rename your jar to fit the following
naming conventions: ```plugin-[name]-[version].jar```.

### install a plugin from GitHub

To install a plugin from GitHub you have to add a JSON-object inside the ```available``` object.
The object has the plugin name as key and contains owner and repository as body.
For example:

```
"available" : {
  "TrafficCollection": {
    "owner": "[owner]",
    "repo": "[repository-name]"
  }
}
```

## Sources of error

Make sure the versions of your imports line up with the current/desired version of your application.