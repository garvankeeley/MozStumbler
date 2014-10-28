package org.mozilla.mozstumbler.client;

import android.util.Log;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.mozilla.mozstumbler.service.core.http.IHttpUtil;
import org.mozilla.mozstumbler.service.core.http.MockHttpUtil;
import org.mozilla.mozstumbler.client.navdrawer.MainDrawerActivity;

import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Config(emulateSdk = 18)
@RunWith(RobolectricTestRunner.class)
public class MainDrawerActivityTest {

    private MainDrawerActivity activity;

    @Before
    public void setUp() throws Exception {
        activity = Robolectric.newInstanceOf(MainDrawerActivity.class);
    }

    @Test
    public void activityShouldNotBeNull() {
        assertNotNull(activity);
    }


    @Test
    public void testUpdater() {

        class TestUpdater extends Updater {
            public TestUpdater(IHttpUtil simpleHttp) {
                super(simpleHttp);
            }

            @Override
            public boolean wifiExclusiveAndUnavailable() {
                return false;
            }
        }

        IHttpUtil mockHttp = new MockHttpUtil();


        Updater upd = new TestUpdater(mockHttp);
        //assertFalse(upd.checkForUpdates(activity, ""));
       // assertFalse(upd.checkForUpdates(activity, null));
        assertTrue(upd.checkForUpdates(activity, "anything_else"));

        Robolectric.runBackgroundTasks();
        Robolectric.runUiThreadTasks();
        Log.d("xxx", "z");
        while (upd.mIsRunning.get()) {
            Log.d("xxx", "a");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {}
        }

        String latest = upd.mTestValues.get("latest-version");
        assertTrue(latest != null);
        upd.downloadAndInstallUpdate(activity, latest);

        while (upd.mIsRunning.get()) {
            Log.d("xxx", "b");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {}
        }

        String file = upd.mTestValues.get("file");
        assertTrue(file != null);
    }

}
