package org.mozilla.mozstumbler.tests;


import android.content.Context;
import android.content.Intent;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.test.InstrumentationTestCase;
import android.test.mock.MockContext;

import org.mozilla.mozstumbler.service.stumblerthread.scanners.WifiScanner;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class ScannerTest  extends InstrumentationTestCase {


    public void testGPSSpam() {
        // TODO: Spam the GPSScanner
        throw new AssertionError();
    }

    public void testWifiSpam() {
        // Spam the WifiScanner with a single Wifi point repeatedly from the OS
        Context ctx = new MockContext();
        WifiScanner scanner = new WifiScanner(ctx);
        WifiScanner.sIsTestMode = true;
        // OK, so we want to simulate sending hundreds of onReceive calls
        ScanResult  sr = makeScanResult();

        sr.BSSID = "01:00:5e:90:10:10";
        sr.SSID = "log_me";

        Intent intent = new Intent(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        for (int i= 0; i < 1000; i++) {
            scanner.mTestModeFakeScanResults.add(sr);
            scanner.onReceive(ctx, intent);
            scanner.mTestModeFakeScanResults.clear();
        }

        // Check the number of intents that were captured is just 1
        // we should have a cache that makes
        assertEquals(1, scanner._testCaptureBroadcastIntent.size());
    }

    /* This rather fragile, and is ok for test, but is there a better way to do this? */
    public ScanResult makeScanResult() {
        Constructor<?>[] ctors = ScanResult.class.getDeclaredConstructors();
        assertTrue(ctors.length > 0);
        try {
            for (Constructor<?> ctor : ctors) {
                int len = ctor.getParameterTypes().length;
                if (len == 5) {
                    ctor.setAccessible(true);

                    return (ScanResult) ctor.newInstance("", "", "", 0, 0);

                }
                if (len == 6) { // Android 4.4.3 has this constructor
                    ctor.setAccessible(true);
                    return (ScanResult) ctor.newInstance(null, "", "", 0, 0, 0);
                }
           }
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
        assertTrue(false);
        return null;
    }
}
