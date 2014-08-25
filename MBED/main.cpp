#include "mbed.h"

Ticker ticker;
DigitalOut led1(LED1);
DigitalOut led2(LED2);

DigitalOut canSwitch(p21);
DigitalOut ignition(p22);
DigitalOut horn(p23);
DigitalOut lights(p23);

CAN can1(p9, p10);
CAN can2(p30, p29);
char counter = 0;
Serial pc(USBTX, USBRX); // tx, rx
Serial ioioSerial(p28, p27); // tx, rx
int fwd = 0;
int leftright = 0;
int ramp = 0x25;
bool start = false;
bool mode_on = true;
bool debug = false;

bool twoByteMessage = false;
char firstByte;
Timer failsafeTimer;

// Bit counting array
char bits[] = { 0, 1, 1, 2, 1, 2, 2, 3, 1, 2, 2, 3, 2, 3, 3, 4 };

int sgn(int x)
{
    if(x>0) return 1;
    if(x<0) return -1;
    return 0;
}

bool sendCan(int nodeid, char* msg, int len)
{
    bool s = can1.write(CANMessage(nodeid, msg, len));
    if(debug) {
        if(s) pc.printf("Wrote %d bytes to CAN\r\n",len);
        else pc.printf("CAN transmission error\r\n");
    }

    if(debug) {
        pc.printf("> (%d) ", nodeid);
        for(int i=0; i<len; i++) {
            pc.printf("[%2.2X]",msg[i]);
            if(!s && debug) pc.printf(" FAILED",msg[i]);
        }
        pc.printf("\r\n");
    }
    return s;
}

char powerGood[] = { 0xAA, 0x04 };
char a3[] = { 0xA3 };
char a4[] = { 0xA4 };

void sendJoystickKeepalive()
{
    char message4[3] = { 0xb0, 0x01, 0x20 };
    sendCan(64,message4, 3);
}

void sendStartup()
{
    if(!mode_on) return;
    pc.printf("sendStartup()\r\n");

    char message2[3] = { 0xb0, 0x01, 0x23 };
    //sendCan(701,message3, 3);
    //sendCan(669,message3, 3);
    char message4[3] = { 0xb0, 0x01, 0x20 };
    sendCan(64,message4, 3);
    wait_ms(10);
    sendCan(64,message2, 3);
    char message[4] = { 0x30, 0x01, 0x00, 0x0F };
    sendCan(64,message, 4);
    wait_ms(10);
    sendCan(64,message2, 3);
    ramp = 0x20;
    wait_ms(10);
}

void sendJoystick()
{
    // Failsafe mode
    if(failsafeTimer.read_ms() > 500) {
        failsafeTimer.stop();
        fwd -= sgn(fwd);
        leftright -= sgn(leftright);
    }

    if(!mode_on) {
        sendJoystickKeepalive();
        return;
    }
    if(start) {
        sendStartup();
        start = false;
        wait_ms(10);
        return;
    }

    if(debug) pc.printf("sendJoystick() - fwd=%d leftright=%d\r\n",fwd,leftright);
    else wait_ms(10);
    char message[7] = { 0xB0, 0x01, ramp < 0x23 ? 0x23:ramp, 0x03, fwd, 0x02, leftright };
    sendCan(64, message, 7);
    if(ramp < 0x25) ramp += 1;
}

int bitcount(int x)
{
    char highnibble = (x >> 4) & 0xF;
    char lownibble = x & 0xF;
    return bits[highnibble] + bits[lownibble];
}

void processTwoByte(char a, char b)
{
    pc.printf("Two-byte message received: [%2.2X] [%2.2X]\n",a,b);
    int fwdData = (a & 0x7F);
    int lrData = (b >> 1) & 0x3F;
    int parity = b & 1;
    int bits = bitcount(fwdData)+bitcount(lrData)+1+parity;
    if(bits % 2 == 1) {
        pc.printf("Parity failure (bit count is %d)\n",bits);
        return;
    }
    failsafeTimer.reset();
    failsafeTimer.start();
    // Decode two's complement
    if(fwdData > 0x40) fwdData -= 128;
    if(lrData > 0x20) lrData -= 64;
    pc.printf("Processed into Forward: %d Left: %d\n",fwdData, lrData);
    fwd = fwdData;
    leftright = lrData;
}

void processByte(char c)
{
    if(c>=0x80) {
        // First byte of a joystick message
        // second byte must be >0x80!
        twoByteMessage = true;
        firstByte = c;
        return;
    } else if(twoByteMessage) {
        twoByteMessage = false;
        processTwoByte(firstByte, c);
        return;
    }
    switch(c) {
        case 'j':
            mode_on = !mode_on;
            if(mode_on) start = true;
            break;
        case 'm':
            pc.printf("Log marked\r\n");
            break;
        case 'w':
            fwd+=16;
            break;
        case 's':
            fwd-=16;
            break;
        case 'd':
            leftright+=16;
            break;
        case 'a':
            leftright-=16;
            break;
        case 'z':
            debug = !debug;
            break;
    }
}


int main()
{
    can1.frequency(105263);
    can2.frequency(105263);
    pc.baud(115200);
    ioioSerial.baud(9600);
    failsafeTimer.start();
    pc.printf("---initialised---\r\n");
    ticker.attach(&sendJoystick, 0.05);

    canSwitch = 0;
    ignition = 0;

    wait_ms(500);
    canSwitch = 1; // To Joystick unit
    wait_ms(500);
    ignition = 1;
    wait_ms(500);
    ignition = 0;
    wait_ms(1000);
    canSwitch = 0;

    CANMessage msg;
    while(1) {
        if(can1.read(msg)) {

            if(debug) pc.printf("(%d) ", msg.id);
            for(int i=0; i<msg.len; i++) {
                if(debug) pc.printf("[%2.2X]",msg.data[i]);
            }
            if(debug) pc.printf("\r\n");
            led2 = !led2;
        }
        char c = 0;
        if(ioioSerial.readable()) {
            c = ioioSerial.getc();
            pc.printf("%c",c);
        } else {
            if(pc.readable()) {
                c = pc.getc();
            }
        }
        if(c!=0) {
            processByte(c);
        }
    }
}