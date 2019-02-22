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
![api10_main3](https://cloud.githubusercontent.com/assets/3366666/24080737/3214e570-0ca5-11e7-88d6-d635cab4a5c3.png)
![api10_summary](https://cloud.githubusercontent.com/assets/3366666/24080746/5818c5a2-0ca5-11e7-907c-dee16f740bba.png)
![api24_settings](https://cloud.githubusercontent.com/assets/3366666/24080751/5ba597f4-0ca5-11e7-9583-adb7b61322cc.png)

## Download
[<img src="https://f-droid.org/badge/get-it-on.png"
      alt="Get it on F-Droid"
      height="80">](https://f-droid.org/app/net.fabiszewski.ulogger)
## Help
- μlogger's current status is shown by two leds, one for location tracking and one for web synchronization: 

led | tracking | synchronization
-|-------- | ---------------
![status green](https://placehold.it/10/00ff00/000000?text=+) |  on, recently updated | synchronized
![status yellow](https://placehold.it/10/ffe600/000000?text=+) | on, long time since last update | synchronization delay
![status red](https://placehold.it/10/ff0000/000000?text=+) | off | synchronization error

- clicking on current track's name will show track statistics

## Translations
- translations may be contributed via [Transifex](https://www.transifex.com/bfabiszewski/ulogger-android/).

## License
- GPL, either version 3 or any later
