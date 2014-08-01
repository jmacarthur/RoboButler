package com.srimech.RoboButlerApp;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.ToggleButton;

import ioio.lib.api.exception.ConnectionLostException;
import ioio.lib.util.AbstractIOIOActivity;
import ioio.lib.api.IOIO;
import ioio.lib.api.DigitalOutput;

public class RBStartActivity extends AbstractIOIOActivity
{

    private TextView textView;
    private ToggleButton toggleButton;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        textView = (TextView) findViewById(R.id.TextView);
	toggleButton = (ToggleButton) findViewById(R.id.ToggleButton);
	enableUI(false);
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
