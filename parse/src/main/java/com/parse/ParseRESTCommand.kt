/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse

import com.parse.NoObjectsEncoder.Companion.get
import com.parse.boltsinternal.Task
import com.parse.http.ParseHttpBody
import com.parse.http.ParseHttpRequest
import com.parse.http.ParseHttpResponse
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONStringer
import java.io.IOException
import java.io.InputStream
import java.net.MalformedURLException
import java.net.URL
import java.util.*

/**
 * A helper object to send requests to the server.
 */
internal open class ParseRESTCommand : ParseRequest<JSONObject?> {
    @JvmField
    val jsonParameters: JSONObject?

    // Headers
    val sessionToken: String?
    var masterKey: String? = null

    /* package */
    @JvmField
    var httpPath: String?
    var installationId: String? = null
    var operationSetUUID: String? = null
    var localId: String?

    constructor(
        httpPath: String?,
        httpMethod: ParseHttpRequest.Method?,
        parameters: Map<String?, *>?,
        sessionToken: String?
    ) : this(
        httpPath,
        httpMethod,
        if (parameters != null) get().encode(parameters) as JSONObject else null,
        sessionToken
    )

    constructor(
        httpPath: String?,
        httpMethod: ParseHttpRequest.Method?,
        jsonParameters: JSONObject?,
        sessionToken: String?
    ) : this(httpPath, httpMethod, jsonParameters, null, sessionToken) {
    }

    private constructor(
        httpPath: String?,
        httpMethod: ParseHttpRequest.Method?,
        jsonParameters: JSONObject?,
        localId: String?, sessionToken: String?
    ) : super(httpMethod!!, createUrl(httpPath)) {
        this.httpPath = httpPath
        this.jsonParameters = jsonParameters
        this.localId = localId
        this.sessionToken = sessionToken
    }

    constructor(builder: Init<*>) : super(builder.method, createUrl(builder.httpPath)) {
        sessionToken = builder.sessionToken
        installationId = builder.installationId
        masterKey = builder.masterKey
        httpPath = builder.httpPath
        jsonParameters = builder.jsonParameters
        operationSetUUID = builder.operationSetUUID
        localId = builder.localId
    }

    protected open fun addAdditionalHeaders(requestBuilder: ParseHttpRequest.Builder) {
        if (installationId != null) {
            requestBuilder.addHeader(HEADER_INSTALLATION_ID, installationId)
        }
        if (sessionToken != null) {
            requestBuilder.addHeader(HEADER_SESSION_TOKEN, sessionToken)
        }
        if (masterKey != null) {
            requestBuilder.addHeader(HEADER_MASTER_KEY, masterKey)
        }
    }

    override fun newRequest(
        method: ParseHttpRequest.Method,
        url: String?,
        uploadProgressCallback: ProgressCallback?
    ): ParseHttpRequest {
        val request: ParseHttpRequest = if (jsonParameters != null && method != ParseHttpRequest.Method.POST && method != ParseHttpRequest.Method.PUT) {
                // The request URI may be too long to include parameters in the URI.
                // To avoid this problem we send the parameters in a POST request json-encoded body
                // and add a http method override parameter in newBody.
                super.newRequest(ParseHttpRequest.Method.POST, url, uploadProgressCallback)
            } else {
                super.newRequest(method, url, uploadProgressCallback)
            }
        val requestBuilder = ParseHttpRequest.Builder(request)
        addAdditionalHeaders(requestBuilder)
        return requestBuilder.build()
    }

    public override fun newBody(uploadProgressCallback: ProgressCallback?): ParseHttpBody? {
        if (jsonParameters == null) {
            val message = String.format(
                "Trying to execute a %s command without body parameters.",
                method.toString()
            )
            throw IllegalArgumentException(message)
        }
        return try {
            var parameters: JSONObject = jsonParameters
            if (method == ParseHttpRequest.Method.GET ||
                method == ParseHttpRequest.Method.DELETE
            ) {
                // The request URI may be too long to include parameters in the URI.
                // To avoid this problem we send the parameters in a POST request json-encoded body
                // and add a http method override parameter.
                parameters = JSONObject(jsonParameters.toString())
                parameters.put(PARAMETER_METHOD_OVERRIDE, method.toString())
            }
            ParseByteArrayHttpBody(parameters.toString(), "application/json")
        } catch (e: Exception) {
            throw RuntimeException(e.message)
        }
    }

    override fun executeAsync(
        client: ParseHttpClient,
        uploadProgressCallback: ProgressCallback?,
        downloadProgressCallback: ProgressCallback?,
        cancellationToken: Task<Void>?
    ): Task<JSONObject?> {
        resolveLocalIds()
        return super.executeAsync(
            client, uploadProgressCallback, downloadProgressCallback, cancellationToken
        )
    }

    public override fun onResponseAsync(
        response: ParseHttpResponse?,
        downloadProgressCallback: ProgressCallback?
    ): Task<JSONObject?>? {
        val content: String
        var responseStream: InputStream? = null
        try {
            responseStream = response!!.content
            content = String(ParseIOUtils.toByteArray(responseStream))
        } catch (e: IOException) {
            return Task.forError(e)
        } finally {
            ParseIOUtils.closeQuietly(responseStream)
        }

        // We need to check for errors differently in /1/ than /2/ since object data in /2/ was
        // encapsulated in "data" and everything was 200, but /2/ everything is in the root JSON,
        // but errors are status 4XX.
        // See https://quip.com/4pbbA9HbOPjQ
        val statusCode = response!!.statusCode
        if (statusCode in 200..599) { // Assume 3XX is handled by http library
            val json: JSONObject
            return try {
                json = JSONObject(content)
                if (statusCode in 400..499) { // 4XX
                    return Task.forError(
                        newPermanentException(
                            json.optInt("code"),
                            json.optString("error")
                        )
                    )
                } else if (statusCode >= 500) { // 5XX
                    return Task.forError(
                        newTemporaryException(
                            json.optInt("code"),
                            json.optString("error")
                        )
                    )
                }
                Task.forResult(json)
            } catch (e: JSONException) {
                Task.forError(newTemporaryException("bad json response", e))
            }
        }
        return Task.forError(newPermanentException(ParseException.OTHER_CAUSE, content))
    }// Include the session token in the cache in order to avoid mixing permissions.

    // Creates a somewhat-readable string that uniquely identifies this command.
    val cacheKey: String
        get() {
            var json: String? = if (jsonParameters != null) {
                try {
                    toDeterministicString(jsonParameters)
                } catch (e: JSONException) {
                    throw RuntimeException(e.message)
                }
            } else {
                ""
            }

            // Include the session token in the cache in order to avoid mixing permissions.
            if (sessionToken != null) {
                json += sessionToken
            }
            return String.format(
                "ParseRESTCommand.%s.%s.%s",
                method.toString(),
                ParseDigestUtils.md5(httpPath),
                ParseDigestUtils.md5(json)
            )
        }

    fun toJSONObject(): JSONObject {
        val jsonObject = JSONObject()
        try {
            if (httpPath != null) {
                jsonObject.put("httpPath", httpPath)
            }
            jsonObject.put("httpMethod", method.toString())
            if (jsonParameters != null) {
                jsonObject.put("parameters", jsonParameters)
            }
            if (sessionToken != null) {
                jsonObject.put("sessionToken", sessionToken)
            }
            if (localId != null) {
                jsonObject.put("localId", localId)
            }
        } catch (e: JSONException) {
            throw RuntimeException(e.message)
        }
        return jsonObject
    }

    /**
     * If this was the second save on a new object while offline, then its objectId wasn't yet set
     * when the command was created, so it would have been considered a "create". But if the first
     * save succeeded, then there is an objectId now, and it will be mapped to the localId for this
     * command's result. If so, change the "create" operation to an "update", and add the objectId to
     * the command.
     */
    private fun maybeChangeServerOperation() {
        if (localId != null) {
            val objectId = localIdManager.getObjectId(localId!!)
            if (objectId != null) {
                localId = null
                httpPath += String.format("/%s", objectId)
                url = createUrl(httpPath)
                if (httpPath!!.startsWith("classes") && method == ParseHttpRequest.Method.POST) {
                    method = ParseHttpRequest.Method.PUT
                }
            }
        }
    }

    fun resolveLocalIds() {
        try {
            val localPointers = ArrayList<JSONObject>()
            getLocalPointersIn(jsonParameters, localPointers)
            for (pointer in localPointers) {
                val localId = pointer["localId"] as String
                val objectId = localIdManager.getObjectId(localId)
                    ?: throw IllegalStateException(
                        "Tried to serialize a command referencing a new, unsaved object."
                    )
                pointer.put("objectId", objectId)
                pointer.remove("localId")
            }
            maybeChangeServerOperation()
        } catch (e: JSONException) {
            // Well, nothing to do here...
        }
    }

    /**
     * Finds all of the local ids in this command and increments their retain counts in the on-disk
     * store. This should be called immediately before serializing the command to disk, so that we
     * know we might need to resolve these local ids at some point in the future.
     */
    fun retainLocalIds() {
        if (localId != null) {
            localIdManager.retainLocalIdOnDisk(localId!!)
        }
        try {
            val localPointers = ArrayList<JSONObject>()
            getLocalPointersIn(jsonParameters, localPointers)
            for (pointer in localPointers) {
                val localId = pointer["localId"] as String
                localIdManager.retainLocalIdOnDisk(localId)
            }
        } catch (e: JSONException) {
            // Well, nothing to do here...
        }
    }

    /**
     * Finds all of the local ids in this command and decrements their retain counts in the on-disk
     * store. This should be called when removing a serialized command from the disk, when we know
     * that we will never need to resolve these local ids for this command again in the future.
     */
    fun releaseLocalIds() {
        if (localId != null) {
            localIdManager.releaseLocalIdOnDisk(localId!!)
        }
        try {
            val localPointers = ArrayList<JSONObject>()
            getLocalPointersIn(jsonParameters, localPointers)
            for (pointer in localPointers) {
                val localId = pointer["localId"] as String
                localIdManager.releaseLocalIdOnDisk(localId)
            }
        } catch (e: JSONException) {
            // Well, nothing to do here...
        }
    }

    /* package */
    internal abstract class Init<T : Init<T>?> {
        var masterKey: String? = null
        var sessionToken: String? = null
        var installationId: String? = null
        var method = ParseHttpRequest.Method.GET
        var httpPath: String? = null
        var jsonParameters: JSONObject? = null
        var operationSetUUID: String? = null
        var localId: String? = null

        /* package */
        abstract fun self(): T
        fun sessionToken(sessionToken: String?): T {
            this.sessionToken = sessionToken
            return self()
        }

        fun installationId(installationId: String?): T {
            this.installationId = installationId
            return self()
        }

        fun masterKey(masterKey: String?): T {
            this.masterKey = masterKey
            return self()
        }

        fun method(method: ParseHttpRequest.Method): T {
            this.method = method
            return self()
        }

        fun httpPath(httpPath: String?): T {
            this.httpPath = httpPath
            return self()
        }

        fun jsonParameters(jsonParameters: JSONObject?): T {
            this.jsonParameters = jsonParameters
            return self()
        }

        fun operationSetUUID(operationSetUUID: String?): T {
            this.operationSetUUID = operationSetUUID
            return self()
        }

        fun localId(localId: String?): T {
            this.localId = localId
            return self()
        }
    }

    class Builder : Init<Builder>() {
        override fun  /* package */self(): Builder {
            return this
        }

        fun build(): ParseRESTCommand {
            return ParseRESTCommand(this)
        }
    }

    companion object {
        /* package */
        const val HEADER_APPLICATION_ID = "X-Parse-Application-Id"

        /* package */
        const val HEADER_CLIENT_KEY = "X-Parse-Client-Key"

        /* package */
        const val HEADER_APP_BUILD_VERSION = "X-Parse-App-Build-Version"

        /* package */
        const val HEADER_APP_DISPLAY_VERSION = "X-Parse-App-Display-Version"

        /* package */
        const val HEADER_OS_VERSION = "X-Parse-OS-Version"

        /* package */
        const val HEADER_INSTALLATION_ID = "X-Parse-Installation-Id"

        /* package */
        const val USER_AGENT = "User-Agent"
        private const val HEADER_SESSION_TOKEN = "X-Parse-Session-Token"
        private const val HEADER_MASTER_KEY = "X-Parse-Master-Key"
        private const val PARAMETER_METHOD_OVERRIDE = "_method"

        // Set via Parse.initialize(Configuration)
        /* package */
        @JvmField
        var server: URL? = null
        private val localIdManager: LocalIdManager
            get() = ParseCorePlugins.getInstance().localIdManager

        @JvmStatic
        fun fromJSONObject(jsonObject: JSONObject): ParseRESTCommand {
            val httpPath = jsonObject.optString("httpPath")
            val httpMethod = ParseHttpRequest.Method.fromString(jsonObject.optString("httpMethod"))
            val sessionToken = jsonObject.optString("sessionToken", null)
            val localId = jsonObject.optString("localId", null)
            val jsonParameters = jsonObject.optJSONObject("parameters")
            return ParseRESTCommand(httpPath, httpMethod, jsonParameters, localId, sessionToken)
        }

        private fun createUrl(httpPath: String?): String {
            // We send all parameters for GET/HEAD/DELETE requests in a post body,
            // so no need to worry about query parameters here.
            return if (httpPath == null) {
                server.toString()
            } else try {
                URL(server, httpPath).toString()
            } catch (ex: MalformedURLException) {
                throw RuntimeException(ex)
            }
        }

        // Encodes the object to JSON, but ensures that JSONObjects
        // and nested JSONObjects are encoded with keys in alphabetical order.
        @JvmStatic
        @Throws(JSONException::class)
        fun toDeterministicString(o: Any): String {
            val stringer = JSONStringer()
            addToStringer(stringer, o)
            return stringer.toString()
        }

        // Uses the provided JSONStringer to encode this object to JSON, but ensures that JSONObjects and
        // nested JSONObjects are encoded with keys in alphabetical order.
        @Throws(JSONException::class)
        private fun addToStringer(stringer: JSONStringer, o: Any) {
            if (o is JSONObject) {
                stringer.`object`()
                val `object` = o
                val keyIterator = `object`.keys()
                val keys = ArrayList<String>()
                while (keyIterator.hasNext()) {
                    keys.add(keyIterator.next())
                }
                keys.sort()
                for (key in keys) {
                    stringer.key(key)
                    addToStringer(stringer, `object`.opt(key))
                }
                stringer.endObject()
                return
            }
            if (o is JSONArray) {
                val array = o
                stringer.array()
                for (i in 0 until array.length()) {
                    addToStringer(stringer, array[i])
                }
                stringer.endArray()
                return
            }
            stringer.value(o)
        }

        /* package */
        @JvmStatic
        fun isValidCommandJSONObject(jsonObject: JSONObject): Boolean {
            return jsonObject.has("httpPath")
        }

        // This function checks whether a json object is a valid /2 ParseCommand json.
        /* package */
        @JvmStatic
        fun isValidOldFormatCommandJSONObject(jsonObject: JSONObject): Boolean {
            return jsonObject.has("op")
        }

        @Throws(JSONException::class)
        protected fun getLocalPointersIn(container: Any?, localPointers: ArrayList<JSONObject>) {
            if (container is JSONObject) {
                if ("Pointer" == container.opt("__type") && container.has("localId")) {
                    localPointers.add(container)
                    return
                }
                val keyIterator = container.keys()
                while (keyIterator.hasNext()) {
                    val key = keyIterator.next()
                    getLocalPointersIn(container[key], localPointers)
                }
            }
            if (container is JSONArray) {
                for (i in 0 until container.length()) {
                    getLocalPointersIn(container[i], localPointers)
                }
            }
        }
    }
}