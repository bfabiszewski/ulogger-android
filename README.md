# ![ulogger_logo_small](https://cloud.githubusercontent.com/assets/3366666/24080878/0288f046-0ca8-11e7-9ffd-753e5c417756.png) μlogger [![Build Status](https://travis-ci.com/bfabiszewski/ulogger-android.svg?branch=master)](https://travis-ci.com/bfabiszewski/ulogger-android) [![Coverity Status](https://scan.coverity.com/projects/12109/badge.svg)](https://scan.coverity.com/projects/bfabiszewski-ulogger-android)

μlogger [*micro-logger*] is an android application for continuous logging of location coordinates, designed to record hiking, biking tracks and other outdoor activities. 
Application works in background. Track points are saved at chosen intervals and may be uploaded to dedicated server in real time.
This client works with [μlogger web server](https://github.com/bfabiszewski/ulogger-server). 
Together they make a complete self owned and controlled client–server solution.

## Features
- meant to be simple and small (*μ*);
- low memory and battery impact;
- uses GPS or network based location data;
- synchronizes location with web server in real time, in case of problems keeps retrying;
- alternatively works in offline mode; positions may be uploaded to the servers manually;
- configurable tracking settings
- export to GPX format

## Screenshots
![api10_main3](https://user-images.githubusercontent.com/3366666/57197957-79f92400-6f6d-11e9-8f61-f1318587dac1.png)
![api10_summary](https://user-images.githubusercontent.com/3366666/57197958-79f92400-6f6d-11e9-8adb-249aaca078d9.png)
![api24_settings](https://user-images.githubusercontent.com/3366666/57197959-79f92400-6f6d-11e9-8a5a-98b9a2b4bd95.png)

## Download
[![Download from f-droid](https://img.shields.io/f-droid/v/net.fabiszewski.ulogger.svg?color=green)](https://f-droid.org/app/net.fabiszewski.ulogger)

## Help
- μlogger's current status is shown by two leds, one for location tracking and one for web synchronization: 

led | tracking | synchronization
-|-------- | ---------------
![status green](https://placehold.it/10/00ff00/000000?text=+) |  on, recently updated | synchronized
![status yellow](https://placehold.it/10/ffe600/000000?text=+) | on, long time since last update | synchronization delay
![status red](https://placehold.it/10/ff0000/000000?text=+) | off | synchronization error

- clicking on current track's name will show track statistics

## Contribute translations
[![Translate with transifex](https://img.shields.io/badge/translate-transifex-green.svg)](https://www.transifex.com/bfabiszewski/ulogger/)


## Donate
[![Donate paypal](https://img.shields.io/badge/donate-paypal-green.svg)](https://www.paypal.me/bfabiszewski)  
![Donate bitcoin](https://img.shields.io/badge/donate-bitcoin-green.svg) `bc1qt3uwhze9x8tj6v73c587gprhufg9uur0rzxhvh`  
![Donate ethereum](https://img.shields.io/badge/donate-ethereum-green.svg) `0x100C31C781C8124661413ed6d1AA9B1e2328fFA2`  

## License
[![License: GPL 3.0](https://img.shields.io/badge/license-GPL--3.0-green.svg)](https://www.gnu.org/licenses/gpl-3.0)  
