/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client.mapview.tiles;

import android.content.Context;
import android.graphics.Color;

import org.mozilla.mozstumbler.BuildConfig;
import org.mozilla.osmdroid.ResourceProxy;
import org.mozilla.osmdroid.tileprovider.tilesource.ITileSource;
import org.mozilla.osmdroid.tileprovider.tilesource.XYTileSource;
import org.mozilla.osmdroid.tileprovider.util.SimpleInvalidationHandler;
import org.mozilla.osmdroid.views.MapView;

public class LowResMapOverlay extends AbstractMapOverlay {
    public static final int HIGH_ZOOM = AbstractMapOverlay.MIN_ZOOM_LEVEL_OF_MAP;
    public static final int LOW_ZOOM = 11;

    public LowResMapOverlay(int zoomLevel, final Context aContext, boolean isMLSTileStore, MapView mapView) {
        super(aContext);

        ITileSource coverageTileSource;
        if (isMLSTileStore) {
            coverageTileSource = new XYTileSource("MozStumbler Tile Store", null,
                    zoomLevel, zoomLevel,
                    AbstractMapOverlay.TILE_PIXEL_SIZE,
                    AbstractMapOverlay.FILE_TYPE_SUFFIX_PNG,
                    new String[]{BuildConfig.TILE_SERVER_URL});
        } else {
            coverageTileSource = new XYTileSource("MapquestOSM", ResourceProxy.string.mapquest_osm,
                    zoomLevel, zoomLevel,
                    AbstractMapOverlay.TILE_PIXEL_SIZE, ".jpg", new String[]{
                    "http://otile1.mqcdn.com/tiles/1.0.0/map/",
                    "http://otile2.mqcdn.com/tiles/1.0.0/map/",
                    "http://otile3.mqcdn.com/tiles/1.0.0/map/",
                    "http://otile4.mqcdn.com/tiles/1.0.0/map/"});
        }
        this.setLoadingBackgroundColor(Color.TRANSPARENT);
        mTileProvider.setTileRequestCompleteHandler(new SimpleInvalidationHandler(mapView));
        mTileProvider.setTileSource(coverageTileSource);
    }
}
