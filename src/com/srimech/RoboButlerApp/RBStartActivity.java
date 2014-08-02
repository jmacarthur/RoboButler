package com.srimech.RoboButlerApp;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.ToggleButton;
import android.net.ConnectivityManager;
import android.content.Context;
import android.net.NetworkInfo;
import ioio.lib.api.exception.ConnectionLostException;
import ioio.lib.util.AbstractIOIOActivity;
import ioio.lib.api.IOIO;
import ioio.lib.api.DigitalOutput;
import java.net.NetworkInterface;
import java.util.List;
import java.util.Collections;
import java.net.InetAddress;
import org.apache.http.conn.util.InetAddressUtils;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationListener;

public class RBStartActivity extends AbstractIOIOActivity
{

    private TextView textView;
    private TextView networkTextView;
    private TextView IPAddressView;
    private TextView locationTextView;
    private ToggleButton toggleButton;

    private StatusThread statusThread;

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

	public void setup() throws ConnectionLostException {
	    try {
		led = ioio_.openDigitalOutput(IOIO.LED_PIN, true);
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


}
