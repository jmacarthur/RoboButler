package com.srimech.RoboButlerApp;

import android.app.Activity;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.ToggleButton;
import ioio.lib.api.DigitalOutput;
import ioio.lib.api.IOIO;
import ioio.lib.api.Uart;
import ioio.lib.api.exception.ConnectionLostException;
import ioio.lib.util.AbstractIOIOActivity;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyStore;
import java.util.Collections;
import java.util.List;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import org.apache.http.conn.util.InetAddressUtils;

public class RBStartActivity extends AbstractIOIOActivity
{

    private TextView textView;
    private TextView networkTextView;
    private TextView IPAddressView;
    private TextView locationTextView;
    private ToggleButton toggleButton;
    private SocketThread socketThread;
    private static int INSECURE_PORT=6000;
    private static int SSL_PORT=6001;
    private static String TAG = "Gerty";
    private boolean ioioConnected = false;
    private StatusThread statusThread;
    private final static String KEYSTOREPASSWORD="password";
    private OutputStream mbedSerialOut;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        textView = (TextView) findViewById(R.id.TextView);
        networkTextView = (TextView) findViewById(R.id.NetworkTextView);
        IPAddressView = (TextView) findViewById(R.id.IPAddressView);
        locationTextView = (TextView) findViewById(R.id.LocationTextView);
	toggleButton = (ToggleButton) findViewById(R.id.ToggleButton);
	enableUI(false);

	statusThread = new StatusThread();
	statusThread.start(); // TODO: Do we need to suspend it on onPause/onStop?

	boolean ssl = false;
	if (ssl) {
	    socketThread = new SSLThread();
	} else {
	    socketThread = new InsecureThread();
	}
	socketThread.start();

	LocationListener locationListener = new LocationListener() {
		public void onLocationChanged(Location location) {
		    // Called when a new location is found by the network location provider.
		    updateLocation(location.toString());
		}
		public void onProviderDisabled(String s) {
		    updateLocation("DISABLED: "+s);
		}
		public void onStatusChanged(String provider, int status, Bundle extras) { }
		public void onProviderEnabled(String provider) {}
	    };
	LocationManager lm = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
	lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, (float) 0.1, locationListener);
    }

    private void updateLocation(final String l)
    {
	runOnUiThread(new Runnable() {
		@Override
		public void run() {
		    locationTextView.setText(l);
		}
	    });
    }

    private void updateStatus() {
	ConnectivityManager connManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
	NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
	final NetworkInfo.State networkstate = mWifi.getState();
	final String ipAddress = getIpAddress(true); 
	runOnUiThread(new Runnable() {
		@Override
		public void run() {
		    networkTextView.setText(networkstate.name());
		    IPAddressView.setText(ipAddress);
		}
	    });
	
    }
    
    /* The getIpAddress function was compiled by Whome on stackoverflow */
    /* See http://stackoverflow.com/questions/6064510/how-to-get-ip-address-of-the-device */

    public String getIpAddress(boolean useIPv4) {
	try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String sAddr = addr.getHostAddress().toUpperCase();
                        boolean isIPv4 = InetAddressUtils.isIPv4Address(sAddr); 
                        if (useIPv4) {
                            if (isIPv4) 
                                return sAddr;
                        } else {
                            if (!isIPv4) {
                                int delim = sAddr.indexOf('%'); // drop ip6 port suffix
                                return delim<0 ? sAddr : sAddr.substring(0, delim);
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) { } // for now eat exceptions
        return "";
    }

    class StatusThread extends Thread
    {
	public void run()
	{
	    updateStatus();
	    try {
		sleep(3);
	    } catch (InterruptedException ignored) {}

	}
    }

    class IOIOThread extends AbstractIOIOActivity.IOIOThread {
	private DigitalOutput led;
	private Uart uart;
	public void setup() throws ConnectionLostException {
	    try {
		led = ioio_.openDigitalOutput(IOIO.LED_PIN, true);
		uart = ioio_.openUart(3, 4, 9600, Uart.Parity.NONE, Uart.StopBits.ONE);
		mbedSerialOut = uart.getOutputStream();
		enableUI(true);
	    } catch (ConnectionLostException e) {
		enableUI(false);
		throw e;
	    }
	}
	
	public void loop() throws ConnectionLostException {
	    try {
		led.write(!toggleButton.isChecked());
	    } catch (ConnectionLostException e) {
		enableUI(false);
		throw e;
	    }
	}
    }

    @Override
    protected AbstractIOIOActivity.IOIOThread createIOIOThread() {
	return new IOIOThread();
    }

    private void enableUI(final boolean enable) {
	ioioConnected = enable;
	runOnUiThread(new Runnable() {
		@Override
		public void run() {
		    toggleButton.setEnabled(enable);
		    setText("IOIO " + (enable ? "ACTIVE":"disconnected"));
		}
	    });
    }
    
    private void setText(final String str) {
	runOnUiThread(new Runnable() {
		@Override
		public void run() {
		    textView.setText(str);
		}
	    });
    }

    abstract class SocketThread extends Thread {
	protected ServerSocket serverSocket;
	protected OutputStream socketOut;
	protected InputStream socketIn;

	public SocketThread() {
	    super();
	    socketOut = null;
	    new Thread(updateThread).start();
	}
	protected abstract void greet() throws IOException;

	protected Runnable updateThread = new Runnable() {
		@Override
		public void run() {
		    for(;;) {
			String status = "";
			if(socketOut!=null) {
			    if(ioioConnected) {
				status += ":";
			    } else {
				status += ".";
			    }
			    try {
				socketOut.write(status.getBytes());
			    } 		catch (IOException e) {
				Log.e(TAG, "Error sending status: "+e);
			    }
			}
			try {
			    Thread.sleep(1000);
			} catch (InterruptedException ex) {
			    // Meh
			}
		    }
		}
	    };

	protected void runLoop()
	{
	    for(;;) {
		try {
		    Socket s = serverSocket.accept();
		    Log.d(TAG, "Socket accepted!");
		    socketOut = s.getOutputStream();
		    socketIn = s.getInputStream();
		    greet();
		    socketOut.write("Hello world".getBytes());
		    for(;;) {
			Log.d(TAG, "Waiting for some input.");
			int b = socketIn.read();
			if(ioioConnected) {
			    mbedSerialOut.write(b);
			} else {
			    socketOut.write("X".getBytes()); //TODO: write isn't thread safe!
			}
			if (b==-1) { break; }
		    }
		}
		catch (IOException ex) {
		    Log.e(TAG, "Failed to accept socket: "+ex);
		}
		try {
		    Thread.sleep(1000);
		} catch(InterruptedException ex) {
		    // meh
		}
	    }

	}
    }

    class InsecureThread extends SocketThread {
	public void run() {
	    Log.d(TAG, "Starting insecure socket thread.");
	    try {
		createSocket();
	    } catch (Exception e) {
		e.printStackTrace();
		Log.d(TAG, "Failed to create socket properly");
		return;
	    }
	    runLoop();
	}
	private void createSocket() throws Exception
	{
	    serverSocket = new ServerSocket(INSECURE_PORT);
	    Log.d(TAG, "Socket created successfully");
	}
	protected void greet() throws IOException
	{
	    socketOut.write("INSECURE server connected.\n".getBytes());
	}
    }

    class SSLThread extends SocketThread {

	public void run()
	{
	    Log.d(TAG, "Starting SSL socket thread.");
	    try {
		createSocket();
	    } catch (Exception e) {
		e.printStackTrace();
		Log.d(TAG, "Failed to create socket properly");
		return;
	    }
	    runLoop();
	}
	private void createSocket() throws Exception
	{
	    // Socket creation code from http://yaragalla.blogspot.co.uk/
	    String keyStoreType = KeyStore.getDefaultType();
	    KeyStore keyStore = KeyStore.getInstance(keyStoreType);
	    InputStream is = getResources().getAssets().open("ServerKeystore");
	    keyStore.load(is, KEYSTOREPASSWORD.toCharArray());

	    String keyalg=KeyManagerFactory.getDefaultAlgorithm();
	    KeyManagerFactory kmf=KeyManagerFactory.getInstance(keyalg);
	    kmf.init(keyStore, KEYSTOREPASSWORD.toCharArray());

	    SSLContext context = SSLContext.getInstance("TLS");
	    context.init(kmf.getKeyManagers(), null, null);
	    SSLServerSocket s =(SSLServerSocket)context.getServerSocketFactory().createServerSocket(SSL_PORT);
	    s.setNeedClientAuth(true);
	    serverSocket = s;
	    Log.d(TAG, "Socket created successfully");
	}
	protected void greet() throws IOException
	{
	    socketOut.write("Secure server connected.\n".getBytes());
	}
    }

}
