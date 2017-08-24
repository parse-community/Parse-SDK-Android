/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import android.location.Criteria;
import android.location.Location;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Locale;

import bolts.Continuation;
import bolts.Task;

/**
 * {@code ParseGeoPoint} represents a latitude / longitude point that may be associated with a key
 * in a {@link ParseObject} or used as a reference point for geo queries. This allows proximity
 * based queries on the key.
 * <p/>
 * Only one key in a class may contain a {@code ParseGeoPoint}.
 * <p/>
 * Example:
 * <pre>
 * ParseGeoPoint point = new ParseGeoPoint(30.0, -20.0);
 * ParseObject object = new ParseObject("PlaceObject");
 * object.put("location", point);
 * object.save();
 * </pre>
 */

public class ParseGeoPoint implements Parcelable {
  static double EARTH_MEAN_RADIUS_KM = 6371.0;
  static double EARTH_MEAN_RADIUS_MILE = 3958.8;

  private double latitude = 0.0;
  private double longitude = 0.0;

  /**
   * Creates a new default point with latitude and longitude set to 0.0.
   */
  public ParseGeoPoint() {
  }

  /**
   * Creates a new point with the specified latitude and longitude.
   *
   * @param latitude
   *          The point's latitude.
   * @param longitude
   *          The point's longitude.
   */
  public ParseGeoPoint(double latitude, double longitude) {
    setLatitude(latitude);
    setLongitude(longitude);
  }

  /**
   * Creates a copy of {@code point};
   *
   * @param point
   *          The point to copy.
   */
  public ParseGeoPoint(ParseGeoPoint point) {
    this(point.getLatitude(), point.getLongitude());
  }

  /**
   * Creates a new point instance from a {@link Parcel} source. This is used when unparceling a
   * ParseGeoPoint. Subclasses that need Parcelable behavior should provide their own
   * {@link android.os.Parcelable.Creator} and override this constructor.
   *
   * @param source The recovered parcel.
   */
  protected ParseGeoPoint(Parcel source) {
    this(source, ParseParcelDecoder.get());
  }

  /**
   * Creates a new point instance from a {@link Parcel} using the given {@link ParseParcelDecoder}.
   * The decoder is currently unused, but it might be in the future, plus this is the pattern we
   * are using in parcelable classes.
   *
   * @param source the parcel
   * @param decoder the decoder
   */
  ParseGeoPoint(Parcel source, ParseParcelDecoder decoder) {
    setLatitude(source.readDouble());
    setLongitude(source.readDouble());
  }

  /**
   * Set latitude. Valid range is (-90.0, 90.0). Extremes should not be used.
   *
   * @param latitude
   *          The point's latitude.
   */
  public void setLatitude(double latitude) {
    if (latitude > 90.0 || latitude < -90.0) {
      throw new IllegalArgumentException("Latitude must be within the range (-90.0, 90.0).");
    }
    this.latitude = latitude;
  }

  /**
   * Get latitude.
   */
  public double getLatitude() {
    return latitude;
  }

  /**
   * Set longitude. Valid range is (-180.0, 180.0). Extremes should not be used.
   *
   * @param longitude
   *          The point's longitude.
   */
  public void setLongitude(double longitude) {
    if (longitude > 180.0 || longitude < -180.0) {
      throw new IllegalArgumentException("Longitude must be within the range (-180.0, 180.0).");
    }
    this.longitude = longitude;
  }

  /**
   * Get longitude.
   */
  public double getLongitude() {
    return longitude;
  }

  /**
   * Get distance in radians between this point and another {@code ParseGeoPoint}. This is the
   * smallest angular distance between the two points.
   *
   * @param point
   *          {@code ParseGeoPoint} describing the other point being measured against.
   */
  public double distanceInRadiansTo(ParseGeoPoint point) {
    double d2r = Math.PI / 180.0; // radian conversion factor
    double lat1rad = latitude * d2r;
    double long1rad = longitude * d2r;
    double lat2rad = point.getLatitude() * d2r;
    double long2rad = point.getLongitude() * d2r;
    double deltaLat = lat1rad - lat2rad;
    double deltaLong = long1rad - long2rad;
    double sinDeltaLatDiv2 = Math.sin(deltaLat / 2.);
    double sinDeltaLongDiv2 = Math.sin(deltaLong / 2.);
    // Square of half the straight line chord distance between both points.
    // [0.0, 1.0]
    double a =
        sinDeltaLatDiv2 * sinDeltaLatDiv2 + Math.cos(lat1rad) * Math.cos(lat2rad)
            * sinDeltaLongDiv2 * sinDeltaLongDiv2;
    a = Math.min(1.0, a);
    return 2. * Math.asin(Math.sqrt(a));
  }

  /**
   * Get distance between this point and another {@code ParseGeoPoint} in kilometers.
   *
   * @param point
   *          {@code ParseGeoPoint} describing the other point being measured against.
   */
  public double distanceInKilometersTo(ParseGeoPoint point) {
    return distanceInRadiansTo(point) * EARTH_MEAN_RADIUS_KM;
  }

  /**
   * Get distance between this point and another {@code ParseGeoPoint} in kilometers.
   *
   * @param point
   *          {@code ParseGeoPoint} describing the other point being measured against.
   */
  public double distanceInMilesTo(ParseGeoPoint point) {
    return distanceInRadiansTo(point) * EARTH_MEAN_RADIUS_MILE;
  }

  /**
   * Asynchronously fetches the current location of the device.
   *
   * This will use a default {@link Criteria} with no accuracy or power requirements, which will
   * generally result in slower, but more accurate location fixes.
   * <p/>
   * <strong>Note:</strong> If GPS is the best provider, it might not be able to locate the device
   * at all and timeout.
   *
   * @param timeout
   *          The number of milliseconds to allow before timing out.
   * @return A Task that is resolved when a location is found.
   *
   * @see android.location.LocationManager#getBestProvider(android.location.Criteria, boolean)
   * @see android.location.LocationManager#requestLocationUpdates(String, long, float, android.location.LocationListener)
   */
  public static Task<ParseGeoPoint> getCurrentLocationInBackground(long timeout) {
    Criteria criteria = new Criteria();
    criteria.setAccuracy(Criteria.NO_REQUIREMENT);
    criteria.setPowerRequirement(Criteria.NO_REQUIREMENT);
    return LocationNotifier.getCurrentLocationAsync(Parse.getApplicationContext(), timeout, criteria)
        .onSuccess(new Continuation<Location, ParseGeoPoint>() {
          @Override
          public ParseGeoPoint then(Task<Location> task) throws Exception {
            Location location = task.getResult();
            return new ParseGeoPoint(location.getLatitude(), location.getLongitude());
          }
        });
  }

  /**
   * Asynchronously fetches the current location of the device.
   *
   * This will use a default {@link Criteria} with no accuracy or power requirements, which will
   * generally result in slower, but more accurate location fixes.
   * <p/>
   * <strong>Note:</strong> If GPS is the best provider, it might not be able to locate the device
   * at all and timeout.
   *
   * @param timeout
   *          The number of milliseconds to allow before timing out.
   * @param callback
   *          callback.done(geoPoint, error) is called when a location is found.
   *
   * @see android.location.LocationManager#getBestProvider(android.location.Criteria, boolean)
   * @see android.location.LocationManager#requestLocationUpdates(String, long, float, android.location.LocationListener)
   */
  public static void getCurrentLocationInBackground(long timeout, LocationCallback callback) {
    ParseTaskUtils.callbackOnMainThreadAsync(getCurrentLocationInBackground(timeout), callback);
  }

  /**
   * Asynchronously fetches the current location of the device.
   *
   * This will request location updates from the best provider that match the given criteria
   * and return the first location received.
   *
   * You can customize the criteria to meet your specific needs.
   * * For higher accuracy, you can set {@link Criteria#setAccuracy(int)}, however result in longer
   *   times for a fix.
   * * For better battery efficiency and faster location fixes, you can set
   *   {@link Criteria#setPowerRequirement(int)}, however, this will result in lower accuracy.
   *
   * @param timeout
   *          The number of milliseconds to allow before timing out.
   * @param criteria
   *          The application criteria for selecting a location provider.
   * @return A Task that is resolved when a location is found.
   *
   * @see android.location.LocationManager#getBestProvider(android.location.Criteria, boolean)
   * @see android.location.LocationManager#requestLocationUpdates(String, long, float, android.location.LocationListener)
   */
  public static Task<ParseGeoPoint> getCurrentLocationInBackground(long timeout, Criteria criteria) {
    return LocationNotifier.getCurrentLocationAsync(Parse.getApplicationContext(), timeout, criteria)
        .onSuccess(new Continuation<Location, ParseGeoPoint>() {
          @Override
          public ParseGeoPoint then(Task<Location> task) throws Exception {
            Location location = task.getResult();
            return new ParseGeoPoint(location.getLatitude(), location.getLongitude());
          }
        });
  }

  /**
   * Asynchronously fetches the current location of the device.
   *
   * This will request location updates from the best provider that match the given criteria
   * and return the first location received.
   *
   * You can customize the criteria to meet your specific needs.
   * * For higher accuracy, you can set {@link Criteria#setAccuracy(int)}, however result in longer
   *   times for a fix.
   * * For better battery efficiency and faster location fixes, you can set
   *   {@link Criteria#setPowerRequirement(int)}, however, this will result in lower accuracy.
   *
   * @param timeout
   *          The number of milliseconds to allow before timing out.
   * @param criteria
   *          The application criteria for selecting a location provider.
   * @param callback
   *          callback.done(geoPoint, error) is called when a location is found.
   *
   * @see android.location.LocationManager#getBestProvider(android.location.Criteria, boolean)
   * @see android.location.LocationManager#requestLocationUpdates(String, long, float, android.location.LocationListener)
   */
  public static void getCurrentLocationInBackground(long timeout, Criteria criteria,
      LocationCallback callback) {
    ParseTaskUtils.callbackOnMainThreadAsync(getCurrentLocationInBackground(timeout, criteria), callback);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null || !(obj instanceof ParseGeoPoint)) {
      return false;
    }
    if (obj == this) {
      return true;
    }
    return ((ParseGeoPoint) obj).getLatitude() == latitude &&
    ((ParseGeoPoint) obj).getLongitude() == longitude;
  }

  @Override
  public String toString() {
    return String.format(Locale.US, "ParseGeoPoint[%.6f,%.6f]", latitude, longitude);
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
    dest.writeDouble(latitude);
    dest.writeDouble(longitude);
  }

  public final static Creator<ParseGeoPoint> CREATOR = new Creator<ParseGeoPoint>() {
    @Override
    public ParseGeoPoint createFromParcel(Parcel source) {
      return new ParseGeoPoint(source, ParseParcelDecoder.get());
    }

    @Override
    public ParseGeoPoint[] newArray(int size) {
      return new ParseGeoPoint[size];
    }
  };
}
