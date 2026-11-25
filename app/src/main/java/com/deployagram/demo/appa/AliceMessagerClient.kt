package com.deployagram.demo.appa

import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

/** AliceMessager answered with a non-success status code, and its reply. */
class AliceMessagerRefused(val statusCode: Int, val reply: String) :
    IOException("AliceMessager answered $statusCode: $reply")

/** Calls AliceMessager's POST /AliceMessager/config. The payload is a JSON object, or no body at all. */
class AliceMessagerClient(
    private val http: OkHttpClient = OkHttpClient(),
) {
    /** Sends [payload] as {"message": payload}, or an empty body when it is null. Reports the reply, such as "World". */
    fun sendConfig(payload: String?, onResult: (Result<String>) -> Unit) {
        val url = AliceMessagerConfig.baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("AliceMessager")
            .addPathSegment("config")
            .build()
        val body: RequestBody = if (payload == null) {
            ByteArray(0).toRequestBody(null)
        } else {
            JSONObject().put("message", payload).toString().toRequestBody(JSON_TYPE)
        }

        http.newCall(Request.Builder().url(url).post(body).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResult(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val text = it.body?.string().orEmpty()
                    val reply = runCatching { JSONObject(text).getString("message") }.getOrDefault(text)
                    onResult(
                        if (it.isSuccessful) Result.success(reply)
                        else Result.failure(AliceMessagerRefused(it.code, reply))
                    )
                }
            }
        })
    }

    private companion object {
        val JSON_TYPE = "application/json".toMediaType()
    }
}
