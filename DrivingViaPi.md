## Driving RoboButler via Pi at EMFCamp

RoboButler is configured to join "emfcamp-insecure" and will report its ip address by sending datagram packets to node.srimech.com. (Jim can receive these)

You can then ssh -L 6000:localhost:6000 pi@<ip address>

Then run "nc -l 127.0.0.1 6000 </dev/ttyACM0 >/dev/ttyACM0" to connect the MBED's serial connection.

You should now be able to run the Python GUI on your computer, altering the connection details to connect to 127.0.0.1:6000. 
Press C and J to connect the joystick, L for lights and arrow keys or any connected joystick to control it.

