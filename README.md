## RoboButler files

This contains all the software used in RoboButler 3000 (except, at the time of writing, the MBED code)

This repository was previously RoboButlerApp, which just contained the Android application.


## Overview

<pre> 
                                    RS232
                      +-------------+   +------+
                  +-->| Android App |-->| IOIO |--+
                  |   +-------------+   +------+  |           CAN
+------------+ TCP|                               |   +------+   +---------+
| Python GUI |----+                               +-->| MBED |-->|  Motor  |
+------------+    |                               |   +------+   | Control |
                  |   +--------------+    USB     |              +---------+
                  +-->| Raspberry Pi |------------+
                      +--------------+
</pre>

Communications between systems is described in the PROTOCOL.md document.