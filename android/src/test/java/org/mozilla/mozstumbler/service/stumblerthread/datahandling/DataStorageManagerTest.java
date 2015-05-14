package org.mozilla.mozstumbler.service.stumblerthread.datahandling;

import android.app.Application;
import android.content.Context;
import android.location.Location;
import android.net.wifi.ScanResult;
import android.provider.ContactsContract;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mozilla.mozstumbler.client.ClientDataStorageManager;
import org.mozilla.mozstumbler.service.Prefs;
import org.mozilla.mozstumbler.service.stumblerthread.Reporter;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.base.JSONRowsObjectBuilder;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.base.JSONRowsStorageManager;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.cellscanner.CellInfo;
import org.mozilla.mozstumbler.svclocator.ServiceLocator;
import org.mozilla.mozstumbler.svclocator.services.log.ILogger;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.fail;
import static org.mozilla.mozstumbler.service.stumblerthread.ReporterTest.createCellInfo;
import static org.mozilla.mozstumbler.service.stumblerthread.ReporterTest.createScanResult;

@Config(emulateSdk = 18)
@RunWith(RobolectricTestRunner.class)
public class DataStorageManagerTest {

    private static final ILogger Log = (ILogger) ServiceLocator.getInstance().getService(ILogger.class);
    private static final String LOG_TAG = LoggerUtil.makeLogTag(DataStorageManagerTest.class);

    private DataStorageManager dm;
    private Reporter rp;
    private Context ctx;

    private Application getApplicationContext() {
        return Robolectric.application;
    }

    int getInMemoryRowCount() {
        Field field = null;
        try {
            field = DataStorageManager.class.getSuperclass().getDeclaredField("mInMemoryActiveJSONRows");
            field.setAccessible(true);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
            fail();
        }
        try {
            return ((JSONRowsObjectBuilder) field.get(dm)).entriesCount();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            fail();
        }
        return -1;
    }

    @Before
    public void setUp() {
        ctx = getApplicationContext();

        // Disable stumble logs
        Prefs.getInstance(ctx).setSaveStumbleLogs(false);
        StorageTracker tracker = new StorageTracker();

        long maxBytes = 20000;
        int maxWeeks = 10;

        // We need to explicitly clear the global instance or else we can't guarantee that the
        // directory will be properly created
        ClientDataStorageManager.sInstance = null;
        dm = ClientDataStorageManager.createGlobalInstance(ctx, tracker, maxBytes, maxWeeks);

        rp = new Reporter();

        // The Reporter class needs a reference to a context
        rp.startup(ctx);

        assertEquals(0, getInMemoryRowCount());
    }

    @Test
    public void testMaxReportsLength() throws JSONException {
        StumblerBundle bundle;

        assertEquals(0, getInMemoryRowCount());

        for (int locCount = 0; locCount < JSONRowsObjectBuilder.MAX_ROWS_IN_MEMORY - 1; locCount++) {
            Location loc = new Location("mock");
            loc.setLatitude(42 + (locCount * 0.1));
            loc.setLongitude(45 + (locCount * 0.1));

            bundle = new StumblerBundle(loc);

            for (int offset = 0; offset < StumblerBundle.MAX_WIFIS_PER_LOCATION * 20; offset++) {
                String bssid = Long.toHexString(offset | 0xabcd00000000L);
                ScanResult scan = createScanResult(bssid, "caps", 3, 11, 10);
                bundle.addWifiData(bssid, scan);
            }

            for (int offset = 0; offset < StumblerBundle.MAX_CELLS_PER_LOCATION * 20; offset++) {
                CellInfo cell = createCellInfo(1, 1, 2000 + offset, 1600199 + offset, 19);
                String key = cell.getCellIdentity();
                bundle.addCellData(key, cell);
            }

            MLSJSONObject mlsObj = bundle.toMLSGeosubmit();

            dm.insert(mlsObj);

            assertEquals((locCount+1) % ReportBatchBuilder.MAX_ROWS_IN_MEMORY,
                    getInMemoryRowCount());
        }

        for (int locCount = ReportBatchBuilder.MAX_ROWS_IN_MEMORY - 1;
             locCount < (ReportBatchBuilder.MAX_ROWS_IN_MEMORY * 2 - 1);
             locCount++) {
            Location loc = new Location("mock");
            loc.setLatitude(42 + (locCount * 0.1));
            loc.setLongitude(45 + (locCount * 0.1));

            bundle = new StumblerBundle(loc);

            for (int offset = 0; offset < StumblerBundle.MAX_WIFIS_PER_LOCATION * 20; offset++) {
                String bssid = Long.toHexString(offset | 0xabcd00000000L);
                ScanResult scan = createScanResult(bssid, "caps", 3, 11, 10);
                bundle.addWifiData(bssid, scan);
            }

            for (int offset = 0; offset < StumblerBundle.MAX_CELLS_PER_LOCATION * 20; offset++) {
                CellInfo cell = createCellInfo(1, 1, 2000 + offset, 1600199 + offset, 19);
                String key = cell.getCellIdentity();
                bundle.addCellData(key, cell);
            }

            MLSJSONObject mlsObj = bundle.toMLSGeosubmit();

            dm.insert(mlsObj);

            assertEquals((locCount+1) % ReportBatchBuilder.MAX_ROWS_IN_MEMORY,
                    getInMemoryRowCount());

        }

    }

    public class StorageTracker implements StorageIsEmptyTracker {
        public void notifyStorageStateEmpty(boolean isEmpty) {
        }
    }
}
