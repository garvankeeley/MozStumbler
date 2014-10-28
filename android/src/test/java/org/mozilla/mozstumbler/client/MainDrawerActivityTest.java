package org.mozilla.mozstumbler.client;

import android.util.Log;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.mozilla.mozstumbler.service.core.http.HttpUtil;
import org.mozilla.mozstumbler.service.core.http.IHttpUtil;
import org.mozilla.mozstumbler.service.core.http.MockHttpUtil;
import org.mozilla.mozstumbler.client.navdrawer.MainDrawerActivity;

import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;

import java.util.Hashtable;

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

        ShadowLog.stream = System.out;

        Robolectric.getFakeHttpLayer().interceptHttpRequests(false);
        IHttpUtil mockHttp = new HttpUtil();

        ShadowLog.stream = System.out;

        Updater upd = new TestUpdater(mockHttp);
        assertFalse(upd.checkForUpdates(activity, ""));
        assertFalse(upd.checkForUpdates(activity, null));

        upd.mTestValues = new Hashtable<String, String>();

        assertTrue(upd.checkForUpdates(activity, "anything_else"));

        Robolectric.runBackgroundTasks();
        Robolectric.runUiThreadTasks();

        String latest = upd.mTestValues.get("latest-version");
        assertTrue(latest != null);
        upd.downloadAndInstallUpdate(activity, latest);

        String file = upd.mTestValues.get("file");
        Log.d(getClass().getSimpleName(), "got file:" + file);
        assertTrue(file != null);
    }

}
