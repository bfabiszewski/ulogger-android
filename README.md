# μlogger

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

## Help
- μlogger's current status is shown by two leds, one for location tracking and one for web synchronization: 

led | tracking | synchronization
-|-------- | ---------------
![status green](https://placehold.it/10/00ff00/000000?text=+) |  on, recently updated | synchronized
![status yellow](https://placehold.it/10/ffe600/000000?text=+) | on, long time since last update | synchronization delay
![status red](https://placehold.it/10/ff0000/000000?text=+) | off | synchronization error

- clicking on current track's name will show track statistics

## License
- GPL, either version 3 or any later