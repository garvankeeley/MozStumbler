package org.osmdroid.tileprovider.modules;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Map;

import org.apache.http.Header;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.HttpHostConnectException;

import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.core.logging.Log;
import org.osmdroid.http.HttpClientFactory;
import org.osmdroid.tileprovider.MapTile;
import org.osmdroid.tileprovider.util.StreamUtils;
import org.osmdroid.tileprovider.tilesource.ITileSource;

/*
 * This is a self contained tile downloader and writer to disk.
 *
 * Features that this has over the regular MapTileDownloader include:
 * - HTTP 404 filtering so that repeated requests that result in a 404 NotFound
 *   will be cached for one hour.
 * - ETag headers are written to disk in a .etag file so that
 *   condition-get can be implemented using an "If-None-Match" request header
 */
public class TileDownloaderDelegate {

    public static final String ETAG_MATCH_HEADER = "If-None-Match";

    public static final int ONE_HOUR_MS = 1000*60*60;

    private final INetworkAvailablityCheck networkAvailablityCheck;
    private final TileWriter tileWriter;

    // We use an LRU cache to track any URLs that give us a HTTP 404.
    private static final int HTTP404_CACHE_SIZE = 2000;
    Map<String, Long> HTTP404_CACHE = Collections.synchronizedMap(new LruCache<String, Long>(HTTP404_CACHE_SIZE));

    private static final String LOG_TAG = AppGlobals.LOG_PREFIX + TileDownloaderDelegate.class.getSimpleName();

    public TileDownloaderDelegate(INetworkAvailablityCheck pNetworkAvailablityCheck,
                                  TileWriter tw) {
        tileWriter = tw;
        networkAvailablityCheck = pNetworkAvailablityCheck;
    }

    /*
     * Check if the tile is current by running a conditional get on
     * the URL
     */
    public boolean isTileCurrent(ITileSource tileSource, MapTile tile) throws HttpHostConnectException {
        if (tileSource == null) {
            return false;
        }

        if (networkIsUnavailable()) {
            return false;
        }

        final String tileURLString = tileSource.getTileURLString(tile);

        if (tileURLString == null || tileURLString.length() == 0) {
            return false;
        }

        if (urlIs404Cached(tileURLString)) {
            // Note that this behaviour is different than
            // actually downloading a tile.  If the URL is 404 cached,
            // we just assume that the tile is 'current'
            return true;
        }

        if (System.currentTimeMillis() < tileWriter.readCacheControl(tileSource, tile)) {
            return true;
        }

        try {
            String etag = tileWriter.readEtag(tileSource, tile);
            if (etag == null) {
                // No etags mean we want to download the file, no need
                // to go checking the etag status over the network.
                return false;
            }

            final HttpClient client = HttpClientFactory.createHttpClient();
            final HttpGet httpGet = new HttpGet(tileURLString);
            httpGet.setHeader(CoreProtocolPNames.USER_AGENT, "osmdroid");
            httpGet.setHeader(ETAG_MATCH_HEADER, etag);
            final HttpResponse response = client.execute(httpGet);

            // Check to see if we got success
            final org.apache.http.StatusLine statusLine = response.getStatusLine();
            if (statusLine.getStatusCode() == 304) {
                return true;
            }
        } catch (HttpHostConnectException hostEx) {
            throw hostEx;
        } catch (IOException ioEx) {
            Log.e(LOG_TAG, "IOException loading tile from network", ioEx);
        }

        return false;
    }

    /*
     * Write a tile from network to disk.
     */
    public boolean downloadTile(ITileSource tileSource, MapTile tile) throws HttpHostConnectException {
        if (tileSource == null) {
            return false;
        }

        if (networkIsUnavailable()) {
            return false;
        }

        final String tileURLString = tileSource.getTileURLString(tile);

        if (tileURLString == null || tileURLString.length() == 0) {
            return false;
        }

        if (urlIs404Cached(tileURLString)) {
            return false;
        }

        // Always try remove the tileURL from the cache before we try
        // downloading again.
        HTTP404_CACHE.remove(tileURLString);

        try {
            final HttpClient client = HttpClientFactory.createHttpClient();
            HttpGet httpGet = new HttpGet(tileURLString);
            httpGet.setHeader(CoreProtocolPNames.USER_AGENT, "osmdroid");
            final HttpResponse response = client.execute(httpGet);

            // Check to see if we got success
            final org.apache.http.StatusLine statusLine = response.getStatusLine();
            if (statusLine.getStatusCode() != 200) {
                if (statusLine.getStatusCode() == 404) {
                    HTTP404_CACHE.put(tileURLString, System.currentTimeMillis() + ONE_HOUR_MS);
                } else {
                    Log.w(LOG_TAG, "Unexpected response from tile server: [" + statusLine.toString() +"]");
                }
                return false;
            }

            final HttpEntity entity = response.getEntity();
            if (entity == null) {
                return false;
            }

            InputStream in = null;
            ByteArrayOutputStream dataStream = null;
            OutputStream out = null;

            try {
                in = entity.getContent();
                dataStream = new ByteArrayOutputStream();
                out = new BufferedOutputStream(dataStream, StreamUtils.IO_BUFFER_SIZE);
                StreamUtils.copy(in, out);
                out.flush();
            } finally {
                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException ioEx) {
                        Log.e(LOG_TAG, "Error closing tile output stream.", ioEx);
                    }
                }
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException ioEx) {
                        Log.e(LOG_TAG, "Error closing tile output stream.", ioEx);
                    }
                }
            }

            final byte[] data = dataStream.toByteArray();
            final ByteArrayInputStream byteStream = new ByteArrayInputStream(data);
            Header etagHeader = response.getFirstHeader("etag");

            String etag;
            if (etagHeader == null) {
                etag = null;
            } else {
                etag = etagHeader.getValue();
            }

            // write the data using the TileWriter
            tileWriter.saveFile(tileSource, tile, byteStream, etag);
            return true;

        } catch (HttpHostConnectException hostEx) {
            throw hostEx;
        } catch (IOException ioEx) {
            Log.e(LOG_TAG, "IOException loading tile from network", ioEx);
        }

        return false;
    }

    /*
     * If a networkAvailabilityCheck object exists, check if the
     * network is *unavailable* and return true.
     *
     * In all other cases, assume the network is available.
     */
    private boolean networkIsUnavailable() {
        if (networkAvailablityCheck != null && !networkAvailablityCheck.getNetworkAvailable()) {
            return true;
        }
        return false;
    }

    /*
     * Check if this URL is already known to 404 on us.
     */
    private boolean urlIs404Cached(String url) {
        Long cacheTs = HTTP404_CACHE.get(url);
        if (cacheTs != null) {
            if (cacheTs.longValue() > System.currentTimeMillis()) {
                return true;
            }
        }
        return false;
    }

}

