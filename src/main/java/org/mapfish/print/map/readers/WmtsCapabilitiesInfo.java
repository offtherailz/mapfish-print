package org.mapfish.print.map.readers;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.locationtech.jts.geom.Envelope;

/**
 * Created by Jesse on 1/20/14.
 */
public class WmtsCapabilitiesInfo {
    private final String identifier;
    private final String title;
    private final List<String> formats;
    private final Envelope bounds;
    private final Map<String, WMTSServiceInfo.TileMatrixSet> tileMatrices;

    public WmtsCapabilitiesInfo(String identifier, String title, List<String> formats, Envelope bounds, Map<String,
            WMTSServiceInfo.TileMatrixSet> tileMatrices) {
        this.identifier = identifier;
        this.title = title;
        this.formats = Collections.unmodifiableList(formats);
        this.bounds = bounds;
        this.tileMatrices = Collections.unmodifiableMap(tileMatrices);
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getTitle() {
        return title;
    }

    public List<String> getFormats() {
        return formats;
    }

    public Envelope getBounds() {
        return bounds;
    }

    public Map<String, WMTSServiceInfo.TileMatrixSet> getTileMatrices() {
        return tileMatrices;
    }
}
