# How to Set Up Your Android Phone (Pixel 6a)

This documentation gives you a walk through on how to set up your Android Phone for the `scala-appanalyzer`.
Given that there is a hughe variety of different Phones out there we will walk through it for the Google Pixel 6a, hoping that the steps taken do transfer to other phones.
We assume that you want to perform in depth traffic analysis later on. Otherwise a stock phone should work perfectly well and no further setup is required.

## 1. Rooting

If you want to perform any kind of traffic analysis with the phone the very first thing you need to do is to root your cell phone.
There are [tutorials out there](https://www.xda-developers.com/how-to-unlock-bootloader-root-magisk-google-pixel-6a/) on how to do this so we won't cover this here, but rooting is essential for the next steps.

## 2. Install Frida

We require frida to perform dynamic hooking of apps, e.g., to disable advanced ssl features preventing us from using the man in the middle proxy to successfully intercept https communication.

1. Download Frida [here]()
