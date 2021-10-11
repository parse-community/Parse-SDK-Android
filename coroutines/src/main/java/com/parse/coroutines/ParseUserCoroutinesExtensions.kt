@file:JvmName("ParseUserCoroutinesExtensions")

package com.parse.coroutines

import com.parse.ParseUser
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

suspend fun ParseUser.suspendSignUp(): ParseUser {
    return suspendCoroutine { continuation ->
        signUpInBackground { e ->
            if (e == null) continuation.resume(this)
            else continuation.resumeWithException(e)
        }
    }
}

suspend fun parseLogIn(username: String, password: String): ParseUser {
    return suspendCoroutine { continuation ->
        ParseUser.logInInBackground(username, password) { user, e ->
            if (e == null) continuation.resume(user)
            else continuation.resumeWithException(e)
        }
    }
}
