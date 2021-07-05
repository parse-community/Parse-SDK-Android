/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse

import android.content.*
import android.net.ConnectivityManager
import com.parse.PLog.v
import java.util.*

internal class ConnectivityNotifier : BroadcastReceiver() {
    private val lock = Any()
    private val listeners: MutableSet<ConnectivityListener> = HashSet()
    private var hasRegisteredReceiver = false
    fun addListener(delegate: ConnectivityListener) {
        synchronized(lock) { listeners.add(delegate) }
    }

    fun removeListener(delegate: ConnectivityListener) {
        synchronized(lock) { listeners.remove(delegate) }
    }

    private fun tryToRegisterForNetworkStatusNotifications(context: Context): Boolean {
        synchronized(lock) {
            return if (hasRegisteredReceiver) {
                true
            } else try {
                context.applicationContext.registerReceiver(
                    this,
                    IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
                )
                hasRegisteredReceiver = true
                true
            } catch (e: ReceiverCallNotAllowedException) {
                // In practice, this only happens with the push service, which will trigger a retry soon afterwards.
                v(
                    TAG, "Cannot register a broadcast receiver because the executing " +
                            "thread is currently in a broadcast receiver. Will try again later."
                )
                false
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        var listenersCopy: List<ConnectivityListener>
        synchronized(lock) { listenersCopy = ArrayList(listeners) }
        for (delegate in listenersCopy) {
            delegate.networkConnectivityStatusChanged(context, intent)
        }
    }

    interface ConnectivityListener {
        fun networkConnectivityStatusChanged(context: Context?, intent: Intent?)
    }

    companion object {
        private const val TAG = "com.parse.ConnectivityNotifier"
        private val singleton = ConnectivityNotifier()
        @JvmStatic
        fun getNotifier(context: Context): ConnectivityNotifier {
            singleton.tryToRegisterForNetworkStatusNotifications(context)
            return singleton
        }

        @JvmStatic
        fun isConnected(context: Context): Boolean {
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetworkInfo
            return network != null && network.isConnected
        }
    }
}