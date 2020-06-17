package com.parse.google

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.parse.boltsinternal.Continuation
import com.parse.boltsinternal.Task
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.parse.LogInCallback
import com.parse.ParseException
import com.parse.ParseUser
import com.parse.SaveCallback

/**
 * Provides a set of utilities for using Parse with Google.
 */
@Suppress("MemberVisibilityCanBePrivate", "unused")
object ParseGoogleUtils {

    private const val AUTH_TYPE = "google"
    private var clientId: String? = null

    private val lock = Any()

    private var isInitialized = false
    private var googleSignInClient: GoogleSignInClient? = null

    /**
     * Just hope this doesn't clash I guess...
     */
    private const val REQUEST_CODE_GOOGLE_SIGN_IN = 62987

    private var currentCallback: LogInCallback? = null

    /**
     * Initializes [ParseGoogleUtils] with the [clientId]. If you have Firebase configured, you can
     * easily get the [clientId] value via context.getString(R.string.default_web_client_id)
     * @param clientId the server clientId
     */
    @JvmStatic
    fun initialize(clientId: String) {
        isInitialized = true
        this.clientId = clientId
    }

    /**
     * @param user A [com.parse.ParseUser] object.
     * @return `true` if the user is linked to a Facebook account.
     */
    @JvmStatic
    fun isLinked(user: ParseUser): Boolean {
        return user.isLinked(AUTH_TYPE)
    }

    /**
     * Log in using a Google.
     *
     * @param activity The activity which passes along the result via [onActivityResult]
     * @param callback The [LogInCallback] which is invoked on log in success or error
     */
    @JvmStatic
    fun logIn(activity: Activity, callback: LogInCallback) {
        checkInitialization()
        this.currentCallback = callback
        val googleSignInClient = buildGoogleSignInClient(activity)
        this.googleSignInClient = googleSignInClient
        activity.startActivityForResult(googleSignInClient.signInIntent, REQUEST_CODE_GOOGLE_SIGN_IN)
    }

    /**
     * The method that should be called from the Activity's or Fragment's onActivityResult method.
     *
     * @param requestCode The request code that's received by the Activity or Fragment.
     * @param resultCode  The result code that's received by the Activity or Fragment.
     * @param data        The result data that's received by the Activity or Fragment.
     * @return true if the result could be handled.
     */
    @JvmStatic
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != REQUEST_CODE_GOOGLE_SIGN_IN) {
            return false
        }
        if (requestCode == REQUEST_CODE_GOOGLE_SIGN_IN) {
            if (data != null) {
                handleSignInResult(data)
            } else {
                onSignInCallbackResult(null, null)
            }
        }
        return true
    }

    /**
     * Unlink a user from a Facebook account. This will save the user's data.
     *
     * @param user     The user to unlink.
     * @param callback A callback that will be executed when unlinking is complete.
     * @return A task that will be resolved when linking is complete.
     */
    @JvmStatic
    fun unlinkInBackground(user: ParseUser, callback: SaveCallback): Task<Void> {
        return callbackOnMainThreadAsync(unlinkInBackground(user), callback, false)
    }

    /**
     * Unlink a user from a Google account. This will save the user's data.
     *
     * @param user The user to unlink.
     * @return A task that will be resolved when unlinking has completed.
     */
    @JvmStatic
    fun unlinkInBackground(user: ParseUser): Task<Void> {
        checkInitialization()
        return user.unlinkFromInBackground(AUTH_TYPE)
    }

    /**
     * Link an existing Parse user with a Google account using authorization credentials that have
     * already been obtained.
     *
     * @param user        The Parse user to link with.
     * @param account Authorization credentials of a Google user.
     * @return A task that will be resolved when linking is complete.
     */
    @JvmStatic
    fun linkInBackground(user: ParseUser, account: GoogleSignInAccount): Task<Void> {
        return user.linkWithInBackground(AUTH_TYPE, getAuthData(account))
    }

    private fun checkInitialization() {
        synchronized(lock) {
            check(isInitialized) { "You must call ParseGoogleUtils.initialize() before using ParseGoogleUtils" }
        }
    }

    private fun handleSignInResult(result: Intent) {
        GoogleSignIn.getSignedInAccountFromIntent(result)
                .addOnSuccessListener { googleAccount ->
                    onSignedIn(googleAccount)
                }
                .addOnFailureListener { exception ->
                    onSignInCallbackResult(null, exception)
                }
    }

    private fun onSignedIn(account: GoogleSignInAccount) {
        googleSignInClient?.signOut()?.addOnCompleteListener {}
        val authData: Map<String, String> = getAuthData(account)
        ParseUser.logInWithInBackground(AUTH_TYPE, authData)
                .continueWith { task ->
                    when {
                        task.isCompleted -> {
                            val user = task.result
                            onSignInCallbackResult(user, null)
                        }
                        task.isFaulted -> {
                            onSignInCallbackResult(null, task.error)
                        }
                        else -> {
                            onSignInCallbackResult(null, null)
                        }
                    }
                }
    }

    private fun getAuthData(account: GoogleSignInAccount): Map<String, String> {
        val authData = mutableMapOf<String, String>()
        authData["id"] = account.id!!
        authData["id_token"] = account.idToken!!
        account.email?.let { authData["email"] = it }
        account.photoUrl?.let { authData["photo_url"] = it.toString() }
        return authData
    }

    private fun onSignInCallbackResult(user: ParseUser?, exception: Exception?) {
        val exceptionToThrow = when (exception) {
            is ParseException -> exception
            null -> null
            else -> ParseException(exception)
        }
        currentCallback?.done(user, exceptionToThrow)
    }

    private fun buildGoogleSignInClient(context: Context): GoogleSignInClient {
        val signInOptions = GoogleSignInOptions.Builder()
                .requestId()
                .requestEmail()
                .requestIdToken(clientId)
                .build()
        return GoogleSignIn.getClient(context, signInOptions)
    }

    /**
     * Calls the callback after a task completes on the main thread, returning a Task that completes
     * with the same result as the input task after the callback has been run.
     */
    private fun <T> callbackOnMainThreadAsync(
            task: Task<T>, callback: SaveCallback, reportCancellation: Boolean): Task<T> {
        return callbackOnMainThreadInternalAsync(task, callback, reportCancellation)
    }

    /**
     * Calls the callback after a task completes on the main thread, returning a Task that completes
     * with the same result as the input task after the callback has been run. If reportCancellation
     * is false, the callback will not be called if the task was cancelled.
     */
    private fun <T> callbackOnMainThreadInternalAsync(
            task: Task<T>, callback: Any?, reportCancellation: Boolean): Task<T> {
        if (callback == null) {
            return task
        }
        val tcs: Task<T>.TaskCompletionSource = Task.create()
        task.continueWith<Void>(Continuation { task ->
            if (task.isCancelled && !reportCancellation) {
                tcs.setCancelled()
                return@Continuation null
            }
            Task.UI_THREAD_EXECUTOR.execute {
                try {
                    var error = task.error
                    if (error != null && error !is ParseException) {
                        error = ParseException(error)
                    }
                    if (callback is SaveCallback) {
                        callback.done(error as ParseException)
                    } else if (callback is LogInCallback) {
                        callback.done(
                                task.result as ParseUser, error as ParseException)
                    }
                } finally {
                    when {
                        task.isCancelled -> {
                            tcs.setCancelled()
                        }
                        task.isFaulted -> {
                            tcs.setError(task.error)
                        }
                        else -> {
                            tcs.setResult(task.result)
                        }
                    }
                }
            }
            null
        })
        return tcs.task
    }
}