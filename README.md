# AppAnalyzer

The scala-appanalyzer is a tool for traffic collection for mobile applications
for iOS, Android and AVDs (Android Emulators). It allows for plugin import
to enable a wide range of functions.

## "global" requirements

| requirement                                                          | installation                 |
|----------------------------------------------------------------------|------------------------------|
| [python](https://www.python.org/)                                    | recommended: python3         |
| [mitmproxy](https://mitmproxy.org/)                                  | ```pip install mitmproxy```  |
| [node & npm](https://nodejs.org/en)                                  | recommended: node 16 & npm 8 |
| [frida](https://frida.re/)                                           | ```npm i -g frida```         |
| [Appium](http://appium.io/docs/en/2.0/)                              | ```npm i -g appium```        |
| [OpenJDK](https://openjdk.org/)                                      | Java 17                      |
| [scala](https://www.scala-lang.org/)                                 | i.e. via sbt                 |
| [sbt](https://www.scala-sbt.org/download.html)                       | i.e. via Coursier            |
| [Postgres](https://www.postgresql.org/)                              | via install script           |
| [AndroidStudio with CLI-tools](https://developer.android.com/studio) | via tar                      |

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

## Android Virtual Devices or Emulator

If you are working with an emulated device make sure that you have sudo rights before continuing. 
For AVDs (the Android Studio Emulator) you can use [rootAVD](https://github.com/newbit1/rootAVD).

## Appium

Make sure to install the correct driver for your device type. 
I.e. ```UIAutomator2``` for Android via ```appium driver install uiautomator2```.

## Installing frida

After deciding what medium you want to inject with code, you will have to get the correct release for your 
CLI version from here ```https://github.com/frida/frida/releases```.
Make sure to use the correct version for your mediums os (i.e. older AVDs use just x86 and not x86_64).
Then push the file to your medium, change the access rights to executable and verify 
it can run with ```./frida-server &```.

### Example for Android and AVD

```
adb push /path/to/frida-server-android /data/local/tmp/frida-server
adb shell chmod 755 /data/local/tmp/frida-server
adb shell /data/local/tmp/frida-server &
```
or for a rooted AVD
```
adb push /path/to/frida-server-android /data/local/tmp/frida-server
adb shell 'su -c chmod 755 /data/local/tmp/frida-server'
adb shell 'su -c /data/local/tmp/frida-server &'
```

## Setting up the MITM proxy

First start by adding the dependencies for the mitmproxy script:
```
npm i -g dotenv
pip3 install psycopg2
```

if you are working with an Android device also add objection:
```
pip3 install objection
```

Under Android objection is used to disable ssl pinning thus the installation of the CA-certificate **can** be skipped.
It is recommended to install the CA-certificate as well.
Follow ```https://docs.mitmproxy.org/stable/concepts-certificates/``` for device specific instructions for installing
the certificate.

Then import the sql schema provided under ```resources/schema.sql```.

### Installing/Running scala-appanalzyer

1. go into the git folder on the same level where the `build.sbt` resides
2. `sbt package`
3. `mv example.config.json config.json`
4. `mkdir plugins`

You can now run the appanalyzer using `aa.sh`

#### Installing a publicly available plugin

1. to show all available plugins `./aa.sh plugin list available`
2. select the plugin of interest and install via `./aa.sh plugin install <pluginNameCaseSensitive>`

#### Installing a custom plugin

1. create the `jar` of the corresponding plugin project
2. move the created jar into the `plugins` folder of the `scala-appanalyzer`

#### Running a Plugin

**Depending on what you want to do/measure ensure that the setup for the mitm traffic interception is set up (see Traffic Interception section) as well as other technical dependencies**


### Traffic Interception for Physical Devices using OpenWRT Router and iptables

It is recommended to set a static IP for the proxy machine as well as the phone.
When using [OpenWRT](https://reedmideke.github.io/networking/2021/01/04/mitmproxy-openwrt.html) the following 
commands have to be executed on the used OpenWRT WLAN router:
```
# create a routing rule entry with ID 101 (must be unique)
echo 101 mitmproxy >> /etc/iproute2/rt_tables

# Configuration below is ephemeral in case of reboot always check router before collection
# <ip of phone> is the ip of the phone you want to intercept the traffic of
# <ip of proxy> is the ip of the machine you want to use as a proxy (i.e., running this tool)
# <router wlan device> the name of the device being the wlan, in our case it was br-lan (use `ip a` to find your wlan device)
# add rule for phones to be monitored
ip rule add from <ip of phone> lookup mitmproxy
# add rule for forwarding traffic to the proxy host 
ip route add default via <proxy host> dev <router wlan device> table mitmproxy
# set mangling
iptables -t mangle -N mitmproxy
# add rule for port 80
iptables -A mitmproxy -i <router wlan device> -t mangle -p tcp -s <phone ip> --dport 80 -j MARK --set-mark 101
# add rule for port 443
iptables -A mitmproxy -i <router wlan device> -t mangle -p tcp -s <phone ip> --dport 443 -j MARK --set-mark 101
# activating the rules
iptables -t mangle -I PREROUTING -j mitmproxy
# deactivating the rules (after experiment)
iptables -t mangle -D PREROUTING 1
```
The below commands have to be set on the proxy host. This stuff should be ephemeral and not persist after a reboot.
```
# deactivate redirects
sysctl -w net.ipv4.conf.all.send_redirects=0
# allowing ip forwarding
sysctl -w net.ipv4.ip_forward=1
# redirect to the proxy
iptables -t nat -I PREROUTING -i <wlan device> -p tcp --dport 80 -j REDIRECT --to-port 8080
iptables -t nat -I PREROUTING -i <wlan device> -p tcp --dport 443 -j REDIRECT --to-port 8080
```
**DISCLAIMER**: Always test those configurations prior to running your experiments. 
Running mitmproxy and checking if simply using the phone browser shows any requests suffices.

## AppAnalyzer Config

Open the example.config.json and create a config.json with your parameters derived from it.

# Managing plugins

The app analyzer uses a plugin structure, so you can easily implement missing functionality yourself.
This chapter is about compiling and installing your own plugins.

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
