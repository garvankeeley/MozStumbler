/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.stumblerthread.scanners.cellscanner;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.LocalBroadcastManager;

import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.Prefs;
import org.mozilla.mozstumbler.service.stumblerthread.Reporter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class CellScanner {
    public static final String ACTION_BASE = AppGlobals.ACTION_NAMESPACE + ".CellScanner.";
    public static final String ACTION_CELLS_SCANNED = ACTION_BASE + "CELLS_SCANNED";
    public static final String ACTION_CELLS_SCANNED_ARG_CELLS = "cells";
    public static final String ACTION_CELLS_SCANNED_ARG_TIME = AppGlobals.ACTION_ARG_TIME;

    private static final long CELL_MIN_UPDATE_TIME = 1000; // milliseconds

    private final Context mAppContext;
    private final Set<String> mVisibleCells = new HashSet<String>();
    private final ReportFlushedReceiver mReportFlushedReceiver = new ReportFlushedReceiver();
    private final AtomicBoolean mReportWasFlushed = new AtomicBoolean();
    private final ISimpleCellScanner mSimpleCellScanner;
    private Timer mCellScanTimer;
    private Handler mBroadcastScannedHandler;
    private AtomicInteger mScanCount = new AtomicInteger();

    public CellScanner(Context appCtx) {
        mAppContext = appCtx;
        if (AppGlobals.isDebug && Prefs.getInstance(mAppContext).isSimulateStumble()) {
            mSimpleCellScanner = new MockSimpleCellScanner();
        } else {
            mSimpleCellScanner = new SimpleCellScannerImplementation(mAppContext);
        }
    }

    public void start() {
        if (!mSimpleCellScanner.isSupportedOnThisDevice()) {
            return;
        }

        // If the scan timer is active, this will reset the number of times it has run
        mScanCount.set(0);

        // clear cells on next scan
        mReportWasFlushed.set(true);

        if (mCellScanTimer != null) {
            return;
        }

        LocalBroadcastManager.getInstance(mAppContext).registerReceiver(mReportFlushedReceiver,
                new IntentFilter(Reporter.ACTION_NEW_BUNDLE));

        // This is to ensure the broadcast happens from the same thread the CellScanner start() is on
        mBroadcastScannedHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                Intent intent = (Intent) msg.obj;
                LocalBroadcastManager.getInstance(mAppContext).sendBroadcastSync(intent);
            }
        };

        mSimpleCellScanner.start();

        mCellScanTimer = new Timer();

        mCellScanTimer.schedule(new TimerTask() {

            @Override
            public void run() {
                if (!mSimpleCellScanner.isStarted()) {
                    return;
                }

                if (mScanCount.incrementAndGet() > AppGlobals.MAX_SCANS_PER_GPS) {
                    stop();
                    return;
                }

                final long curTime = System.currentTimeMillis();
                ArrayList<CellInfo> cells = new ArrayList<CellInfo>(mSimpleCellScanner.getCellInfo());

                if (mReportWasFlushed.getAndSet(false)) {
                    clearCells();
                }

                if (cells.isEmpty()) {
                    return;
                }

                for (CellInfo cell : cells) {
                    addToCells(cell.getCellIdentity());
                }

                Intent intent = new Intent(ACTION_CELLS_SCANNED);
                intent.putParcelableArrayListExtra(ACTION_CELLS_SCANNED_ARG_CELLS, cells);
                intent.putExtra(ACTION_CELLS_SCANNED_ARG_TIME, curTime);
                // send to handler, so broadcast is not from timer thread
                Message message = new Message();
                message.obj = intent;
                mBroadcastScannedHandler.sendMessage(message);
            }
        }, 0, CELL_MIN_UPDATE_TIME);
    }

    private synchronized void clearCells() {
        mVisibleCells.clear();
    }

    private synchronized void addToCells(String cell) {
        mVisibleCells.add(cell);
    }

    public synchronized void stop() {
        LocalBroadcastManager.getInstance(mAppContext).unregisterReceiver(mReportFlushedReceiver);

        if (mCellScanTimer != null) {
            mCellScanTimer.cancel();
            mCellScanTimer = null;
        }
        mSimpleCellScanner.stop();
    }

    public synchronized int getVisibleCellInfoCount() {
        return mVisibleCells.size();
    }

    private class ReportFlushedReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent i) {
            mReportWasFlushed.set(true);
        }
    }
}
