@file:JvmName("FacebookUserCoroutinesExtensions")
@file:Suppress("unused")

package com.parse.facebook

import android.app.Activity
import androidx.fragment.app.Fragment
import com.parse.ParseUser
import com.parse.facebook.ParseFacebookUtils.logInWithReadPermissionsInBackground
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

suspend fun suspendFacebookLogInWithReadPermissions(
        activity: Activity,
        permissions: Collection<String>
): ParseUser? {
    return suspendCoroutine { continuation ->
        logInWithReadPermissionsInBackground(activity, permissions) { user, err ->
            if (err != null) continuation.resumeWithException(err)
            continuation.resume(user)
        }
    }
}

suspend fun suspendFacebookLogInWithReadPermissions(
        fragment: Fragment,
        permissions: Collection<String>
): ParseUser? {
    return suspendCoroutine { continuation ->
        logInWithReadPermissionsInBackground(fragment, permissions) { user, err ->
            if (err != null) continuation.resumeWithException(err)
            continuation.resume(user)
        }
    }
}
