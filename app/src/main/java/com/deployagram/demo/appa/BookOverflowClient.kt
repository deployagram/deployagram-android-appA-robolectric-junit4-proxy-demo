package com.deployagram.demo.appa

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Callback
import okhttp3.Call
import okhttp3.Response
import java.io.IOException

/** BookOverflow answered with a non-success status code. */
class BookOverflowRefused(val statusCode: Int) : IOException("BookOverflow answered $statusCode")

/** Calls BookOverflow's GET /suggestion?email=... and reports the outcome. */
class BookOverflowClient(
    private val http: OkHttpClient = OkHttpClient(),
) {
    fun suggestionFor(email: String, onResult: (Result<BookSuggestion>) -> Unit) {
        val url = BookOverflowConfig.baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("BookOverflow")
            .addPathSegment("suggestion")
            .addQueryParameter("email", email)
            .build()

        http.newCall(Request.Builder().url(url).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResult(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = it.body?.string().orEmpty()
                    onResult(
                        if (it.isSuccessful) Result.success(BookSuggestion.fromJson(body))
                        else Result.failure(BookOverflowRefused(it.code))
                    )
                }
            }
        })
    }
}
