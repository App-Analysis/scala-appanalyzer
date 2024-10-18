# AppAnalyzer

The scala-appanalyzer is a tool for traffic collection for mobile applications
for iOS, Android and AVDs (Android Emulators). It allows for plugin import
to enable a wide range of functions.

## "global" requirements

| requirement                                                          | installation                 |
|----------------------------------------------------------------------|------------------------------|
| [python](https://www.python.org/)                                    | recommended: python3         |
| [mitmproxy](https://mitmproxy.org/)                                  | ```pip install mitmproxy```  |
| [objection](https://github.com/sensepost/objection)                  | ```pip install objection```  |
| [node & npm](https://nodejs.org/en)                                  | recommended: node 16 & npm 8 |
| [frida](https://frida.re/)                                           | ```npm i -g frida```         |
| [Appium](http://appium.io/docs/en/2.0/)                              | ```npm i -g appium```        |
| [OpenJDK](https://openjdk.org/)                                      | Java 17                      |
| [scala](https://www.scala-lang.org/)                                 | via package manager          |
| [sbt](https://www.scala-sbt.org/download.html)                       | via package manager          |
| [Postgres](https://www.postgresql.org/)                              | via package manager          |
| [AndroidStudio with CLI-tools](https://developer.android.com/studio) | via tar                      |
| [libimobiledevice](https://libimobiledevice.org/)                    | via package manager          |

You might have to install atob as well via ```npm i -g atob```.
CommandLine tools can be installed in Android Studio under ```Settings -> SDK -> SDK Tools```.
It is recommended to add the cli-tools, platform-tools and emulator to your path configuration for easier use.
This is done by adding
```
export PATH="$PATH:~/Android/Sdk/platform-tools"
export PATH="$PATH:~/Android/Sdk/emulator"
export PATH="$PATH:~/Android/Sdk/cmdline-tools/latest/bin"
```
to either your ```.bashrc``` or ```.profile```.
You also have to export your ```ANDROID_HOME``` pointing to your SDK installation.
This can be done via ```export ANDROID_HOME="~/Android/Sdk"```.

## Appanalyzer Configuration

We already provide an example configuration via `example.config.json` which can be renamed to `config.json` and then
adapted. Most values might already work for you but especially the `db` section as well as the paths to the executables
need to be checked and adapted.

### Database 

As we depend on a postgres database you need to create a corresponding database and import the schema 
provided under ```resources/schema.sql```. Make sure to adapt the `./config.json` according to your postgres setup,
i.e., ensure that port, ip, username, database name, and password are correct in the `db` section.

### Appium

Make sure to install the correct driver for your device type.
- Android requires ```UIAutomator2``` which can be installed via ```appium driver install uiautomator2```
- iOS requires `tbd`

### Traffic Interception

As there are multiple viable options we have a dedicated [Readme](./TRAFFIC_INTERCEPTION.md) to set up the traffic 
interception.

### Smartphone Configuration

We have a dedicated documentation on how to set up [Android](/ANDROID.md), [Android Emulator](./ANDROID_EMULATOR.md), and 
[iPhone](./IPHONE.md).

### Plugins

The app analyzer uses a plugin structure and publicly available plugins can be added in the configuration in the `plugin`
section and managed via `./aa.sh plugin ...`. For further instructions read the [plugin documentation](./PLUGIN.md).


## Running A Measurement

You have installed all the dependencies, you have configured the appanalyzer, you have installed a plugin to perform a
measurement. Great! You are ready to start measuring apps now.

The only thing remaining is to create a set of apps you want to measure and put them all into a single folder.
Then you can run `./aa.sh run <Platform> </Path/To/Apks/Folder> plugin <PluginName>` and start the measurement.

It can be worth it to familiarize yourself with the configuration flags by using the available `-h/--help` flags for
each action.

