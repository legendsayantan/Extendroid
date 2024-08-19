# Project Extendroid
#### Adds desktop-like multi-window support, on android os for smartphones.

## Requirements:
1. Android 10 or newer device
2. Shizuku permissions
3. Popup window permission on Xiaomi devices

## Features
- [x] Start other apps in pop-up windows (some apps don't work)
- [x] Scalable windows with variety of resolutions
- [x] Disable physical display but keep apps awake
- [x] Stream and control individual app windows to desktop via usb (needs scrcpy)
- [ ] Stream individual app windows to desktop via wifi
- [ ] Control individual app windows to desktop via wifi

## Usage
Some features are functional, Others are still being developed.
To use it, [Download here](https://github.com/legendsayantan/Extendroid/tree/dev/app/release/). Feel free to report bugs!

### Google blocks it!
**You can safely ignore the security warning from Google.**

Google does not like when apps load external binaries, but this app loads the file app/src/main/assets/classes/DisplayToggle.dex.lock [Virustotal Analysis](https://www.virustotal.com/gui/file/2a303ca1273c62c86b30af12fc457140fe27b60da513773cd1c928c8ebc755f5).
It is required for the "Disable Display" feature, so there are custom security measures implemented in app. 

As for other security, the screen recording or screencast icon you may notice are required for functionality.
Unless you are using wifi stream feature, no screen recording is sent anywhere.
You may also just disable internet access for the app. 
