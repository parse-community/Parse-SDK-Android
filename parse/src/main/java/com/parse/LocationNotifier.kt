/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse

import android.content.Context
import android.location.Criteria
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import com.parse.boltsinternal.Capture
import com.parse.boltsinternal.Task
import com.parse.boltsinternal.TaskCompletionSource
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * LocationNotifier is a wrapper around fetching the current device's location. It looks for the GPS
 * and Network LocationProviders by default (printStackTrace()'ing if, for example, the app doesn't
 * have the correct permissions in its AndroidManifest.xml). This class is intended to be used for a
 * *single* location update.
 *
 *
 * When testing, if a fakeLocation is provided (via setFakeLocation()), we don't wait for the
 * LocationManager to fire or for the timer to run out; instead, we build a local LocationListener,
 * then call the onLocationChanged() method manually.
 */
internal object LocationNotifier {
    private var fakeLocation: Location? = null

    /**
     * Asynchronously gets the current location of the device.
     *
     *
     * This will request location updates from the best provider that match the given criteria
     * and return the first location received.
     *
     *
     * You can customize the criteria to meet your specific needs.
     * * For higher accuracy, you can set [Criteria.setAccuracy], however result in longer
     * times for a fix.
     * * For better battery efficiency and faster location fixes, you can set
     * [Criteria.setPowerRequirement], however, this will result in lower accuracy.
     *
     * @param context  The context used to request location updates.
     * @param timeout  The number of milliseconds to allow before timing out.
     * @param criteria The application criteria for selecting a location provider.
     * @see android.location.LocationManager.getBestProvider
     * @see android.location.LocationManager.requestLocationUpdates
     */
    /* package */
    @JvmStatic
    fun getCurrentLocationAsync(
        context: Context,
        timeout: Long, criteria: Criteria?
    ): Task<Location> {
        val tcs = TaskCompletionSource<Location>()
        val timeoutFuture = Capture<ScheduledFuture<*>>()
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val listener: LocationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                timeoutFuture.get().cancel(true)
                tcs.trySetResult(location)
                manager.removeUpdates(this)
            }

            override fun onProviderDisabled(provider: String) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {}
        }
        timeoutFuture.set(ParseExecutors.scheduled().schedule({
            tcs.trySetError(ParseException(ParseException.TIMEOUT, "Location fetch timed out."))
            manager.removeUpdates(listener)
        }, timeout, TimeUnit.MILLISECONDS))
        val provider = manager.getBestProvider(criteria!!, true)
        if (provider != null) {
            manager.requestLocationUpdates(
                provider,  /* minTime */
                0,  /* minDistance */
                0.0f,
                listener
            )
        }
        if (fakeLocation != null) {
            listener.onLocationChanged(fakeLocation!!)
        }
        return tcs.task
    }

    /**
     * Helper method for testing.
     */
    /* package */
    fun setFakeLocation(location: Location?) {
        fakeLocation = location
    }
}