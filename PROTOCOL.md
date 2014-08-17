Communications protocol
-----------------------

There are four units in the RoboButler system:

* User interface
* Android
* MBED
* RPI

If the RPI is used, it replaces Android, although the Android phone will still be used passively to provide Wifi connectivity and GPS data.

The user interface talks to either the Android App or RPI over a TCP socket. The user interface starts a socket connection to Android/RPI. In the RPI case, it's not yet known whether the Android wifi connection will be able to expose the RPI's IP address to the network, so it may be necessary for the RPI to connect outwards to the user interface.

## Interface to RoboButler

On connection, RoboButler will start sending a repeated status byte, one per second.

In the Android case, 128 indicates that the IOIO system is not connected and 129 indicates that it is.
In the RPI case, 128 indicates no connection to the MBED and 129 that the MBED system is connected over PC Serial connection.

The downstream system (Android or RPi) can also send strings back to the server.
These always start with 's' and are terminated by NUL (character 0) or any character above 127.

The Android/RPI system should forward all bytes in the ASCII range (0-127) to the MBED system.

The current messages are:

'j': toggle transmitting joystick commands over CAN. When switching joystick commands on, the MBED will transmit the startup sequence including relay codes to start the joystick unit.
'z': toggle debug mode; this increases the messages sent over the MBED PC Serial connection.
'w', 'a', 's', 'd': Increase or decrease speed in the given axis. Note that this persists; the robot will only be stopped by sending the opposite direction command or 'j'.

## Planned improvements

We need to be able to send joystick commands in as fine a format as possible; most of the interface should be dedicated to updating the motors.

We also need a failsafe system so the MBED stops if it stops recieving messages.

We don't know what the range of joystick values is, but we do know there's only one byte for each axis, which sets a maximum.

We could send a two-byte message, with a 7-bit value for each axis. So, any value above 127 as the first byte, with 7 bits per channel, and one parity bit.

<1 bit joystick command><7 bits forward/back><1 bit parity><7 bits left/right>

MBED should implement some basic acceleration limit.

If no joystick command is given in two seconds then it should start to ramp down towards zero. 

Thus, the MBED needs to know current joystick pos and transmitted joystick pos.

Each cycle, current joystick pos moves towards the last transmitted joystick pos, unless the last joystick pos was recieved more than two seconds ago, in which case it moves towards zero.

We should also have other commands:

* 'p' - power on (starts joystick mode)
* 'o' - power off (stops joystick mode)
* 'h' - horn (1 sec)
* 'A' - 'F': Darlington drives 2-8 on (drives 0 and 1 are the CAN relay and ignition switch)
* 'a' - 'f': Darlington drives 2-8 off.
* 'l': Request location. This should be answered by the Android system or raspberry pi.
* 'j': Request battery status. We don't know if we'll be able to measure main battery level, so this is just a placeholder.

## IP Discovery

Although the android device will show its IP address on screen, it's possible the device will obtain a new address. We assume the Android device or Rpi will be able to access the internet, so it should be able to send a message to a known address. If we keep a port open on node.srimech.com (e.g. 50000) for the duration of the festival we should be able to send the current IP address whenever it updates.

## Camera communication

Once the cameras are active (RPI only, unless we get IP Webcam running on the Android device) they will communicate via a different channel.

