package com.deployagram.demo.appa

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The one screen of AppA: enter an email, press "Recommend book", see the suggestion. "Load Config" calls AliceMessager,
 * sending "Hello" once a book has been recommended, and an empty payload before that.
 */
class MainActivity : Activity() {

    private val bookOverflow = BookOverflowClient()
    private val aliceMessager = AliceMessagerClient()

    private var recommended = false

    private lateinit var result: TextView
    private lateinit var configResult: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val email = EditText(this).apply {
            hint = "Email address"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        val recommend = Button(this).apply {
            text = "Recommend book"
            setOnClickListener { recommendBookFor(email.text.toString().trim()) }
        }
        result = TextView(this).apply {
            gravity = Gravity.START
            textSize = 18f
        }
        val loadConfig = Button(this).apply {
            text = "Load Config"
            setOnClickListener { loadConfig() }
        }
        configResult = TextView(this).apply {
            gravity = Gravity.START
            textSize = 18f
        }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            addView(email)
            addView(recommend)
            addView(result)
            addView(loadConfig)
            addView(configResult)
        })
    }

    private fun recommendBookFor(email: String) {
        result.text = "Asking BookOverflow…"
        bookOverflow.suggestionFor(email) { outcome ->
            runOnUiThread {
                outcome.onSuccess { recommended = true }
                result.text = outcome.fold(
                    onSuccess = { it.describe() },
                    onFailure = { failure ->
                        if (email.isEmpty() && failure is BookOverflowRefused && failure.statusCode == 400) {
                            "You must enter a valid email!"
                        } else {
                            "Sorry, no suggestion right now (${failure.message})"
                        }
                    },
                )
            }
        }
    }

    private fun loadConfig() {
        // "Hello" once a book has been recommended, and an empty payload before that.
        val payload = if (recommended) "Hello" else null
        configResult.text = "Asking AliceMessager…"
        aliceMessager.sendConfig(payload) { outcome ->
            runOnUiThread {
                configResult.text = outcome.fold(
                    onSuccess = { it },
                    onFailure = { failure ->
                        if (failure is AliceMessagerRefused) failure.reply else "Sorry, ${failure.message}"
                    },
                )
            }
        }
    }
}
