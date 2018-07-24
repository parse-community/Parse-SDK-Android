/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import android.os.Parcel;
import android.os.Parcelable;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.List;
import java.util.Locale;

/**
 * {@code ParsePolygon} represents a set of coordinates that may be associated with a key
 * in a {@link ParseObject} or used as a reference point for geo queries. This allows proximity
 * based queries on the key.
 * <p>
 * Example:
 * <pre>
 * List<ParseGeoPoint> points = new ArrayList<ParseGeoPoint>();
 * points.add(new ParseGeoPoint(0,0));
 * points.add(new ParseGeoPoint(0,1));
 * points.add(new ParseGeoPoint(1,1));
 * points.add(new ParseGeoPoint(1,0));
 * ParsePolygon polygon = new ParsePolygon(points);
 * ParseObject object = new ParseObject("PlaceObject");
 * object.put("area", polygon);
 * object.save();
 * </pre>
 */
public class ParsePolygon implements Parcelable {

    public final static Creator<ParsePolygon> CREATOR = new Creator<ParsePolygon>() {
        @Override
        public ParsePolygon createFromParcel(Parcel source) {
            return new ParsePolygon(source, ParseParcelDecoder.get());
        }

        @Override
        public ParsePolygon[] newArray(int size) {
            return new ParsePolygon[size];
        }
    };
    private List<ParseGeoPoint> coordinates;

    /**
     * Creates a new polygon with the specified {@link ParseGeoPoint}.
     *
     * @param coords The polygon's coordinates.
     */
    public ParsePolygon(List<ParseGeoPoint> coords) {
        setCoordinates(coords);
    }

    /**
     * Creates a copy of {@code polygon};
     *
     * @param polygon The polygon to copy.
     */
    public ParsePolygon(ParsePolygon polygon) {
        this(polygon.getCoordinates());
    }

    /**
     * Creates a new point instance from a {@link Parcel} source. This is used when unparceling a
     * ParsePolygon. Subclasses that need Parcelable behavior should provide their own
     * {@link android.os.Parcelable.Creator} and override this constructor.
     *
     * @param source The recovered parcel.
     */
    protected ParsePolygon(Parcel source) {
        this(source, ParseParcelDecoder.get());
    }


    /**
     * Creates a new point instance from a {@link Parcel} using the given {@link ParseParcelDecoder}.
     * The decoder is currently unused, but it might be in the future, plus this is the pattern we
     * are using in parcelable classes.
     *
     * @param source  the parcel
     * @param decoder the decoder
     */
    ParsePolygon(Parcel source, ParseParcelDecoder decoder) {
        setCoordinates(source.readArrayList(null));
    }

    /**
     * Throws exception for invalid coordinates.
     */
    static List<ParseGeoPoint> validate(List<ParseGeoPoint> coords) {
        if (coords.size() < 3) {
            throw new IllegalArgumentException("Polygon must have at least 3 GeoPoints");
        }
        return coords;
    }

    /**
     * Get coordinates.
     */
    public List<ParseGeoPoint> getCoordinates() {
        return coordinates;
    }

    /**
     * Set coordinates. Valid are Array of GeoPoint, ParseGeoPoint or Location
     * at least 3 points
     *
     * @param coords The polygon's coordinates.
     */
    public void setCoordinates(List<ParseGeoPoint> coords) {
        this.coordinates = ParsePolygon.validate(coords);
    }

    /**
     * Get converts coordinate to JSONArray.
     */
    protected JSONArray coordinatesToJSONArray() throws JSONException {
        JSONArray points = new JSONArray();
        for (ParseGeoPoint coordinate : coordinates) {
            JSONArray point = new JSONArray();
            point.put(coordinate.getLatitude());
            point.put(coordinate.getLongitude());
            points.put(point);
        }
        return points;
    }

    /**
     * Checks if this {@code ParsePolygon}; contains {@link ParseGeoPoint}.
     */
    public boolean containsPoint(ParseGeoPoint point) {
        double minX = coordinates.get(0).getLatitude();
        double maxX = coordinates.get(0).getLatitude();
        double minY = coordinates.get(0).getLongitude();
        double maxY = coordinates.get(0).getLongitude();

        for (int i = 1; i < coordinates.size(); i += 1) {
            ParseGeoPoint geoPoint = coordinates.get(i);
            minX = Math.min(geoPoint.getLatitude(), minX);
            maxX = Math.max(geoPoint.getLatitude(), maxX);
            minY = Math.min(geoPoint.getLongitude(), minY);
            maxY = Math.max(geoPoint.getLongitude(), maxY);
        }

        boolean outside = point.getLatitude() < minX || point.getLatitude() > maxX || point.getLongitude() < minY || point.getLongitude() > maxY;
        if (outside) {
            return false;
        }

        boolean inside = false;
        for (int i = 0, j = coordinates.size() - 1; i < coordinates.size(); j = i++) {
            double startX = coordinates.get(i).getLatitude();
            double startY = coordinates.get(i).getLongitude();
            double endX = coordinates.get(j).getLatitude();
            double endY = coordinates.get(j).getLongitude();

            boolean intersect = ((startY > point.getLongitude()) != (endY > point.getLongitude()) &&
                    point.getLatitude() < (endX - startX) * (point.getLongitude() - startY) / (endY - startY) + startX);

            if (intersect) {
                inside = !inside;
            }
        }
        return inside;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof ParsePolygon)) {
            return false;
        }
        if (obj == this) {
            return true;
        }
        ParsePolygon other = (ParsePolygon) obj;

        if (coordinates.size() != other.getCoordinates().size()) {
            return false;
        }

        boolean isEqual = true;
        for (int i = 0; i < coordinates.size(); i += 1) {
            if (coordinates.get(i).getLatitude() != other.getCoordinates().get(i).getLatitude() ||
                    coordinates.get(i).getLongitude() != other.getCoordinates().get(i).getLongitude()) {
                isEqual = false;
                break;
            }
        }
        return isEqual;
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "ParsePolygon: %s", coordinates);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        writeToParcel(dest, ParseParcelEncoder.get());
    }

    void writeToParcel(Parcel dest, ParseParcelEncoder encoder) {
        dest.writeList(coordinates);
    }
}
