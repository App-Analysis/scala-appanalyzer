# How to Set Up Your Android Phone (Pixel 6a)

This documentation gives you a walk through on how to set up your Android Phone for the `scala-appanalyzer`.
Given that there is a hughe variety of different Phones out there we will walk through it for the Google Pixel 6a, 
hoping that the steps taken do transfer to other phones.
We assume that you want to perform in depth traffic analysis later on. Otherwise, a stock phone should work perfectly 
well and no further setup is required.
Finally, we assume a linux environment.

### 1. Rooting

If you want to perform any kind of traffic analysis with the phone the very first thing you need to do is to root your 
cell phone.
There are [tutorials available](https://www.xda-developers.com/how-to-unlock-bootloader-root-magisk-google-pixel-6a/) on how to do this, so we won't cover this here, but rooting is essential for the 
next steps.

### 2. Install Frida

We require [frida](https://frida.re/docs/android/) to perform dynamic hooking of apps, e.g., to disable advanced ssl 
features preventing us from using the man in the middle proxy to successfully intercept https communication.

1. Download `frida-server` [here](https://github.com/frida/frida/releases). You might need to expand the available 
assets list to actually see the frida-server binary selection.
2. Change the name of the extracted binary to `frida-server` as this is how we will refer from it from now on and 
how `scala-appanalyzer` refers to it
3. go into the folder holding the binary and execute `adb push frida-server /data/local/tmp/`
4. we need to make the binary executable on the phone using `adb shell "chmod 755 /data/local/tmp/frida-server"`

`scala-appanalyzer` will start and stop frida on demand so no further steps are required. However, to make sure that 
everything is working we suggest that you start frida once per hand
using `adb shell "su -c /data/local/tmp/frida-server"`. Do not forget to stop it prior to doing any measurements using 
he `scala-appanalyzer`.

If you have trouble starting frida and encounter the error message that it cannot bind to its port. You need to check 
if `frida-server` is currently still running using `adb shell 'ps -e | grep frida-server'` and then kill the 
corresponding process.

### 3. Final Configurations

There are some final steps required for some measurement types.

#### Traffic Interception

If you want to perform traffic interception via `mitmproxy` you need to install its certificate. To do this you have
to follow the instructions [here](./TRAFFIC_INTERCEPTION.md) and then visit `mitm.it` in the browser of your phone.
You then download the certificate for android and install the certificate via the settings.

## Post Setup

You should now be set to use the phone for any measurements. Below we will list a some hints and manual tests we 
experience useful in the past. If you encountered some issues or want to share some experiences feel free to do a PR
and add to the below lists.

### Manual Experiments

Sometimes for debugging purposes some manual testing is useful and we want to share our experience and steps here.

#### Manual Traffic Interception using Objection and mitm

Sometimes we just want to get a glimps into the traffic transmitted by the app and do some basic interception by hand.
For this to work make sure you followed one of the options for traffic interception explained in the 
[corresponding readme](./TRAFFIC_INTERCEPTION.md). We will assume the default port (8080) for the mitm proxy, you need to
adapt the commands accordingly if you this does not hold true for you. We also assume that you have installed 
[objection](https://github.com/sensepost/objection) and that it is available in your `$PATH` variable. Otherwise, you
need to exchange each call to `objection` with its fully qualified path. The easies way to install objection is 
probably ` pipx`.

1. start your local `mitmproxy` instance with the correct port. 
2. start frida on your phone `adb shell "su -c /data/local/tmp/frida-server -v"`
3. if you have not already done so install the app on your phone. If you have the `apk` you can simply run
`adb install -g <app.apk>` with `-g`  granting all relevant rights.
4. start your app using objection `objection --gadget <app.id> explore --startup-command 'android sslpinning disable'`
    1. if you do not know the `app.id` you can run `adb shell 'pm list packages -f'` and grep the output for the name or 
        parts of the name of the app to get the fully qualified name, usually looking like a reverse URL.

While clicking through the app the `mitmproxy` window should now display the intercepted requests. 

### Errors

We encountered some idiosyncrasies while performing measurements and want to start a knowledge base for future users
to not run into those issues or at least know how to fix them.

#### Storage Full

If you install and deinstall a large amount of apps without restarting the phone eventually you will see the error that 
apparently the phones storage is full even though this is not the case. This is due to some counter being full and can
be fixed by simply restarting the phone. We usually started to encounter this issue after about 10k installations.
To ensure a smooth operation for large scale measurements we recommend rebooting your phone prior to any measurement.

