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

import java.util.HashSet;
import java.util.Set;

/**
 * @exclude
 */
public class GcmBroadcastReceiver extends BroadcastReceiver {

    private final Set<BroadcastReceiver> subReceivers = new HashSet<>();

    @Override
    public final void onReceive(Context context, Intent intent) {
        ServiceUtils.runWakefulIntentInService(context, intent, PushService.class);
        for (BroadcastReceiver broadcastReceiver : subReceivers) {
            broadcastReceiver.onReceive(context, intent);
        }
    }

    protected final void addBroadcastReceiver(BroadcastReceiver broadcastReceiver) {
        subReceivers.add(broadcastReceiver);
    }

    protected final void removeBroadcastReceiver(BroadcastReceiver broadcastReceiver) {
        subReceivers.remove(broadcastReceiver);
    }

}
