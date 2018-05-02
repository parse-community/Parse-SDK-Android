/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ReceiverCallNotAllowedException;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** package */ class ConnectivityNotifier extends BroadcastReceiver {
  private static final String TAG = "com.parse.ConnectivityNotifier";
  public interface ConnectivityListener {
    void networkConnectivityStatusChanged(Context context, Intent intent);
  }

  private static final ConnectivityNotifier singleton = new ConnectivityNotifier();
  public static ConnectivityNotifier getNotifier(Context context) {
    singleton.tryToRegisterForNetworkStatusNotifications(context);
    return singleton;
  }

  public static boolean isConnected(Context context) {
    ConnectivityManager connectivityManager =
        (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    if (connectivityManager == null) {
      return false;
    }

    NetworkInfo network = connectivityManager.getActiveNetworkInfo();
    return network != null && network.isConnected();
  }

  private Set<ConnectivityListener> listeners = new HashSet<>();
  private boolean hasRegisteredReceiver = false;
  private final Object lock = new Object();
  
  public void addListener(ConnectivityListener delegate) {
    synchronized (lock) {
      listeners.add(delegate);
    }
  }
  
  public void removeListener(ConnectivityListener delegate) {
    synchronized (lock) {
      listeners.remove(delegate);
    }
  }
  
  private boolean tryToRegisterForNetworkStatusNotifications(Context context) {
    synchronized (lock) {
      if (hasRegisteredReceiver) {
        return true;
      }
      
      try {
        if (context == null) {
          return false;
        }
        context = context.getApplicationContext();
        context.registerReceiver(this, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        hasRegisteredReceiver = true;
        return true;
      } catch (ReceiverCallNotAllowedException e) {
        // In practice, this only happens with the push service, which will trigger a retry soon afterwards.
        PLog.v(TAG, "Cannot register a broadcast receiver because the executing " +
            "thread is currently in a broadcast receiver. Will try again later.");
        return false;
      }
    }
  }
  
  @Override
  public void onReceive(Context context, Intent intent) {
    List<ConnectivityListener> listenersCopy;
    synchronized (lock) {
      listenersCopy = new ArrayList<>(listeners);
    }
    for (ConnectivityListener delegate : listenersCopy) {
      delegate.networkConnectivityStatusChanged(context, intent);
    }
  }
}
