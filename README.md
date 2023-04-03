# App Analyzer

## Requirements

- rooted Android device (tested Galaxy A13)
    - or Android Emulator (not implemented yet)
    - or iPhone
- objection
- frida
- appium
- scala
- sbt
- sshpass
- zipinfo
- cydia
    - use cydia on the iPhone to install and run frida
- postgres
- mitmproxy
- android-studio
    - with command line tools
      - including `apkanalyzer` which needs a java version < 10
      - use `sudo archlinux-java set java-8-openjdk` on Arch
    - with adb

## Installation

1. sbt stage the project
   ```
   sbt stage
   ```
2. start frida server on the target device (the current connected one)
    - `adb shell getprop ro.product.cpu.abi` to get the device's abi
    - grab the [latest matching release](https://github.com/frida/frida/releases/latest) for your device
    - example on android:
       ```
       adb push /path/frida-server-android /data/local/tmp/frida-server
       adb shell chmod 755 /data/local/tmp/frida-server
       ```
      The last command will block your terminal as frida is running 'inside' it.
3. install appium globally
   ```
   npm install -g appium
   ```

4. install frida globally
    - be careful to match the version from frida-server used in step 2
   ```
   npm install -g frida
   ```

4. install objection globally
   ```
   pip3 install objection
   ```

5. install mitmproxy globally
   ```
   pip3 install mitmproxy
   ```

## Configuration

### 1. Basic

- A basic `config.json` is provided. However, each path for the required tools has to be checked and, if required,
  adapted to fit the machine.
- You need to create a postgresql database and import the tables of the functionalities you want to leverage
    - `./resources/basicSchema.sql` is always required
    - do not forget to adapt the `config.json` to match the username, machine, port, database name actually configured
      for the machine

#### 1.1 Useful tips we picked up

- For the Samsung Galaxy A13 after rooting
    - if your phone is annoying with a popup for carrier configuration removing the CID manager helped.
- to check if the framework is working with your set-up, run `./aa.sh android_device run functionalityCheck`

### 2. Traffic Collection

1. You need to prep the Phone to use the host machine and the mitmproxy port as a
   proxy (`~/.mitmproxy/mitmproxy-ca-cert.cer`)
    - in case of an Android phone objection will take care of ssl (though adding the cert never hurts)
    - in case of an iPhone you need to install the root cert of mitmproxy
2. import the sql schema for traffic collection: `./resources/trafficCollection/trafficCollectionSchema.sql`
3. install dependencies required by our mitmproxy script
    - dotenv
    - psycopg2
4. It is highly recommended to fix the IP address for the proxy machine as well as the phone

#### 2.1 OpenWRT Configuration

```
https://reedmideke.github.io/networking/2021/01/04/mitmproxy-openwrt.html
```

The below commands have to be executed on the used openwrt WLAN router

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

**DISCLAIMER:** Always test those configurations prior to running your experiments. Running `mitmproxy` and checking
if simply using the phone browser shows any requests suffices.

### 2.2 Consent Dialog Analysis

- import the sql schema for consent dialog analysis: `./resources/trafficCollection/consentDialogSchema.sql`