import pygame
from pygame.locals import *
import socket
from threading import Thread
import select
import math

netstatus = ("Offline", (127,127,127))
commandSocket = None
ioiostat = False
exitFlag = False
serialString = ""
stringMode = False
clock = pygame.time.Clock()
joyxrange = 64
joyyrange = 64
keyspeed = 4

def sgn(x):
    if x>0: return 1
    if x<0: return -1
    return 0

def drawText(surface, text, pos, colour = (0,0,0)):
    textSurface = font.render(text, True, colour)
    surface.blit(textSurface, pos)

def connect():
    global commandSocket, netstatus
    netstatus = ("Connecting", (0,127,0))
    commandSocket = socket.create_connection(("10.0.1.92", 6000))
    if commandSocket is None:
        print "Connect failed"
        netstatus = ("Failed", (255,0,0))
        return
    socketReadThread = Thread(target = SocketReader, args = (commandSocket,))
    socketReadThread.start()

def SocketReader(commandSocket):
    global netstatus, ioiostat, serialString, stringMode

    netstatus = ("Connected", (0,255,0))
    while not exitFlag:
        ready = select.select([commandSocket], [], [], 1)
        if ready[0]:
            c = commandSocket.recv(1)
            if c == "":
                # Socket has closed
                netstatus = ("Remote closed", (255,0,0))
            print "Receive byte from socket "+c
            if stringMode:
                if c == 0 or c>127:
                    stringMode = false
                    processString(serialString)
                    # Continue processing
                else:
                    serialString += c
                    return
            if c == chr(129):
                ioiostat = True
            elif c == chr(128):
                ioiostat = False
            elif c == "s":
                # Begin reading string
                stringMode = True
    print "SocketReader exited"

def sendSerial(c):
    print "send: [%x]"%(ord(c))
    if commandSocket is not None:
        commandSocket.send(c)
    else:
        print "socket disconnected"

def button(keycode, uc):
    global driveOn
    if keycode == K_c:
        connect()
    elif keycode == K_i:
        driveOn = not driveOn
    elif keycode == K_j:
        sendSerial('j')

def runGameLoop():
    frameCounter = 0
    global driveOn
    joyx = 0
    joyy = 0
    driveOn = False
    joyCentreX = 128
    joyCentreY = 360
    joySize = 64
    joystick = None

    if pygame.joystick.get_count() > 0:
        print "There are %d joysticks." % pygame.joystick.get_count()
        pygame.joystick.Joystick(0).init()
        if pygame.joystick.Joystick(0).get_numaxes >= 4:
            joystick = pygame.joystick.Joystick(0)
            print "Joystick registered"


    while True:
        screen.fill((0,0,255))
        c = frameCounter / 16.0
        delta = (math.pi*2.0/3)
        r = (math.sin(c) * 0.5 + 0.5) * 255;
        g = (math.sin(c+delta) * 0.5 + 0.5) * 255;
        b = (math.sin(c+delta*2) * 0.5 + 0.5) * 255;
        drawText(screen, "RoboButler 3000 interface", (32,32), (r,g,b))
        drawText(screen, netstatus[0], (32,80), netstatus[1])
        drawText(screen, "IOIO: "+str(ioiostat), (32,128))
        drawText(screen, "Joystick "+("ON" if driveOn else "OFF"), (32,180))
        pygame.draw.circle(screen, (255,255,255), (joyCentreX,joyCentreY), joySize, 2)
        pygame.draw.circle(screen, (255,255,255), (joyCentreX+joyx,joyCentreY-joyy), 8, 2)
        pygame.display.flip();
        mouse = pygame.mouse.get_pressed()
        if not mouse[0]:
            # Joystick centring mode
            joyx -= sgn(joyx)
            joyy -= sgn(joyy)

        for event in pygame.event.get():
            if event.type == QUIT:
                return
            elif event.type == KEYDOWN and event.key in [K_ESCAPE,K_q]:
                return
            elif event.type == KEYDOWN:
                button(event.key, event.unicode)
            elif event.type == MOUSEMOTION:
                (x,y) = event.pos
                b = event.buttons[0]
                if b:
                    dx = x - joyCentreX
                    dy = -y + joyCentreY
                    if abs(dx) < joySize and abs(dy) < joySize:
                        dist = dx*dx+dy*dy
                        if(dist < joySize*joySize):
                            joyx = dx
                            joyy = dy

        # Check joysticks...
        if joystick != None:
            joyx = int(joystick.get_axis(2) * joyxrange)
            joyy = int(-joystick.get_axis(1) * joyyrange)

        # Monitor keys and adjust joystick if necessary
        keys = pygame.key.get_pressed()
        if keys[K_UP]:    joyy = min(joyy+keyspeed, joyyrange)
        if keys[K_DOWN]:  joyy = max(joyy-keyspeed, -joyyrange)
        if keys[K_LEFT]:  joyx = min(joyx+keyspeed, joyxrange)
        if keys[K_RIGHT]: joyx = max(joyx-keyspeed, -joyxrange)

        clock.tick(25)
        if driveOn and frameCounter % 5 == 0:
            forward = -joyy
            leftright = joyx
            if forward < 0: forward += 128
            leftright >>= 1 # Left/right is only 6 bit
            if leftright <0: leftright += 64
            setbits = bin(forward).count("1") + bin(leftright).count("1") + 1
            if(setbits %2 == 1): parity = 1
            else: parity = 0
            byte1 = 0x80 | forward
            byte2 = leftright << 1 | parity
            print "Joystick: Fwd %d Left %d\n"%(-joyy,joyx)
            print "Binary sequence: 1 "+bin(forward)+" "+bin(leftright)+" %d"%parity + " [%2.2X,%2.2X]"%(byte1,byte2)
            sendSerial(""+chr(byte1))
            sendSerial(""+chr(byte2))
        frameCounter += 1

def loadImages():
    pass

def main():
    global font, screen, exitFlag
    pygame.init()
    pygame.font.init()
    pygame.joystick.init()
    font = pygame.font.Font(pygame.font.get_default_font(), 48, italic = True)
    font.set_italic(True)
    screen = pygame.display.set_mode((640,480))
    pygame.display.set_caption('ROBOBUTLER 3000')
    loadImages()
    try:
        runGameLoop()
    except Exception as ex:
        print "Exception running main thread: "+str(ex)
    print "Quitting, setting exitFlag to True"
    exitFlag = True

if __name__ == "__main__":
    main()
