# How to get an iPhone 8 up and running with XCUITest

The basic documentation documents, dependencies, resources are:

0. [Appium: Intro](https://appium.io/docs/en/about-appium/intro/)
1. [Apium: Getting Started](https://appium.io/docs/en/about-appium/getting-started/index.html)
2. [Appium: Appium 2.0 Documentation](https://appium.github.io/appium/docs/en/2.0/)
3. [Appium: Platform Support Documentation](https://appium.io/docs/en/about-appium/platform-support/index.html)
4. [Appium: The XCUITest Driver for iOS](https://appium.io/docs/en/drivers/ios-xcuitest/index.html)
5. [GitHub: appium-xcuitest-driver README](https://github.com/appium/appium-xcuitest-driver#readme)
6. [Github: Appium fork of the WebDriver Agent](https://github.com/appium/WebDriverAgent)
   - [the old WebDriverAgent getting started guide](https://github.com/facebookarchive/WebDriverAgent/wiki/Starting-WebDriverAgent) 
7. [Github: Appium Inspector (for manual testing)](https://github.com/appium/appium-inspector)

but they are fairly scattered and there is no clear order or logic in which to do what.

**As of this writing (Dec 2022) Appium is in a major change from 1.x to 2.0. Thus, some documentations were semi-outdated
and might be completely outdated by the point of reading. Pleas be aware.**

This document should mitigate this and pull it all together to work and give directions
to frustrated developer coming after me.


## Dependencies

Consider this a simple list and not a guide or order in which to install stuff. As some stuff might already be deployed on 
a given machine the installation commands might be redundant or a prior uninstall is required to match versions.

- Mac OSX (we are working on a MacMini with an M1 chip)
- node/npm
  - `brew install node` (make sure there is no older installation already present, we need node >= 14 and npm >= 8)
- python3/pip3
  - `brew install python3` 
- libimobiledevice
   `brew install libimobiledevice` (good look, dependency was already installed for me so this command is a guess)
- appium
   - `npm install -g appium@next` (the `@next` is required due to the major version transitioning)
   - xcuitest `appium driver install xcuitest`
     - current xcuitest only (says to) supports current major iOS version and version. Depending on how far in the future we are
        you might need to check out an older driver version.
     - there are even more requirements listed in the repository, check them out.
- xcode
  - you need to register your apple developer account for signing to work 
  - word of warning copied from the xcuitest repo: The newest xcode might not be supported yet
  - `brew install xcode` (never tested this command myself, other steps might be required to install xcode)
  



## A step by step Guide

1. You need an Apple Developer account (free is supposed to work)
   1. retrieve and save theTeam ID as displayed on the ['Certificates, Identifiers & Profiles'](https://developer.apple.com/account/resources/profiles/list) subpage just below your name
      in a file containing nothing else, i.e., `./resources/AppleTeamId`
2. Install `node` and `appium` using npm
   - if you then run `$> appium` in a shell you should see smth like `Welcome to Appium v.2.0` and `No driver have been installed`
3. Install xcuitest driver
   - if you then run `$> appium` in a shell you should now see a line `Available drviers: -xcuitest@<version>`
4. do the WebDriverAgent
   - clone the repository `$>git clone https://github.com/appium/WebDriverAgent`
   - in the repository we do some xcode magic now
      1. ```
         xcodebuild -project WebDriverAgent.xcodeproj \
                    -scheme WebDriverAgentRunner \
                    -destination 'platform=iOS,name=<PhoneName>' \
                    -allowProvisioningUpdates \
                    test \
         DEVELOPER_TEAM=<TEAMID>
         ```
        This command should end with `*** BUILD SUCCEED ***`
   - During this process a lot of screw ups can happen:
     - you have to have your `DEVELOPER_TEAM` registered with Xcode (i.e., you have to be logged in) 
     - typos (if your phone name is wrong you get a neat list of possible targets)
   - As screw-ups will happen lets do some manual verification that they did not:
     1. install a [Appium Inspector Release](https://github.com/appium/appium-inspector/releases)
        1. this requires that you download and install the dmg file
        2. as you just downloaded random stuff from the internet you need to tell MacOS to 'just trust me':
            ```
           xattr -cr "/Applications/Appium\ Inspector.app
            ```
        3. now you can start 'Appium Inspector' from your applications
        4. we now need to start an appium session:
        5. start appium server `appium`
        6. configure Appium Inspector Connection Settings 
        ``` 
             {
                "appium:xcodeOrgId" : "<TEAMID>",
                "appium:xcodeSigningId" : "iPhone Developer",
                "appium:udid" : "auto",
                "platformName" : "iOS",
                "appium:app" : "/path/to/the/app.ipa",
                "appium:automationName" : "XCUITest",
                "appium:deviceName" : "iPhone",
             }
        ```
        7. Connect. Do not worry if you have a socket timeout, just try again. It happens
            - if everything works you should now have a neat remote view of the started app
