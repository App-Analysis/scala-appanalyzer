# How to Set Up the IPhone (iOS 14.5/iPhone 8)

There are two options on configuring the iPhone. The first does not allow for the use of Appium but makes life 
significantly easier and requires only regular linux machine for measurements. The second option does allow to
use Appium but requires quite a few more steps as well as a macOS as the measurement machine. Unless Appium is 
strictly required I highly recommend using the easier option.

## 1. Rooting the iPhone

However, the very first step is that you need to root the iPhone. We have made good experiences using 
[checkra1n](https://checkra.in/). Putting the iPhone into DFU mode is a bit tricky. First press volume up - release,
volume down - release, power until screen turns black. Then press power and volume down for 5s - release the power button
but hold the volume down button for another 10 sections. The screen should stay black if you are successful.
[This](https://help.ifixit.com/article/108-dfu-restore) tutorial proofed helpful.
If the phone is in DFU mode you can run checkra1n from the command line `sudo ./checkra1n -c -q` and when the phone is
booted up you should have a rooted iPhone.

If you encounter an `error code: -79` it can help to use an original lightning cable and make sure that you do not have
faceid or passcode enabled. 
Even if you encounter an error the rooting can still be successful. Just try to open the cydia app to check, if it opens
you are probably set.


## 2. Traffic Interception

Make sure you have installed the `SSL Kill Switch 2` App via Cydia and to follow the instructions of the 
[Traffic Interception Readme](./TRAFFIC_INTERCEPTION.md). After the mitm setup visit `mitm.it` to download and install
the mitm certificate.
You should now be set.

### Sanity Check

To perform a sanity check open the mitmproxy on your test machine and run an `arbitrary.ipa` which you know performs internet
connections.

1. `ideviceinstaller --install arbitrary.ipa` 
2. get the fully qualified name of the app by looking in the list of all installed apps `ideviceinstaller -l`
3. connect to the device via ssh `ssh root@ip` password is `alpine`
4. open the app `open fully.qualified.app.name`

If the app opens and you can see traffic in the mitmproxy window you are all set.

## 3.a No Appium Setup (The Easy One)

To interact with the iPhone you will need the [libimobiledevice](https://libimobiledevice.org/) tooling as well as `sshpass`.
Make sure that you can call `ideviceinfo` and `ideviceinstaller` from the command line, as well as `sshpass`.

## 3.b With Appium Setup (The Really Not So Easy One)

TBD...