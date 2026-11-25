package com.deployagram.demo.appa

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withHint
import androidx.test.espresso.matcher.ViewMatchers.withText
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.hamcrest.CoreMatchers.containsString
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Tests AppA's "Load Config" button, which calls a second back-end, AliceMessager. Both back-ends are MockWebServer
 * doubles on the JVM, so no emulator or network is needed.
 *
 * The button sends "Hello" when a book has already been recommended, and an empty payload when it has not.
 */
@RunWith(RobolectricTestRunner::class)
class LoadConfigButtonTest {

    private val defaultBookOverflowUrl = BookOverflowConfig.baseUrl
    private val defaultAliceMessagerUrl = AliceMessagerConfig.baseUrl
    private lateinit var bookOverflow: MockWebServer
    private lateinit var bookOverflowAnswers: SuggestionAnswers
    private lateinit var aliceMessager: MockWebServer
    private lateinit var enteredEmail: String
    private var screen: ActivityScenario<MainActivity>? = null

    @Before
    fun startBackEnds() {
        enteredEmail = "reader-${UUID.randomUUID()}@example.com"

        bookOverflowAnswers = SuggestionAnswers()
        bookOverflow = MockWebServer()
        bookOverflow.dispatcher = bookOverflowAnswers
        bookOverflow.start()
        BookOverflowConfig.baseUrl = bookOverflow.url("/").toString().trimEnd('/')

        aliceMessager = MockWebServer()
        aliceMessager.dispatcher = AliceMessagerAnswers()
        aliceMessager.start()
        AliceMessagerConfig.baseUrl = aliceMessager.url("/").toString().trimEnd('/')
    }

    @After
    fun stopBackEnds() {
        screen?.close()
        screen = null
        bookOverflow.shutdown()
        aliceMessager.shutdown()
        BookOverflowConfig.baseUrl = defaultBookOverflowUrl
        AliceMessagerConfig.baseUrl = defaultAliceMessagerUrl
    }

    @Test
    fun pressingLoadConfigAfterARecommendationSendsHelloAndShowsWorld() {
        givenBookOverflowHasASuggestion()
        givenABookHasBeenRecommended()

        whenTheLoadConfigButtonIsPressed()

        thenTheReplyIsShown("World")
        thenAliceMessagerWasSentTheConfigRequest(AliceMessagerAnswers.HELLO)
    }

    @Test
    fun pressingLoadConfigWithNoRecommendationSendsAnEmptyPayloadAndShowsTheReply() {
        givenTheScreenIsOpen()

        whenTheLoadConfigButtonIsPressed()

        thenTheReplyIsShown("Is someone there?")
        thenAliceMessagerWasSentTheConfigRequest("")
    }

    // Given

    private fun givenBookOverflowHasASuggestion() {
        bookOverflowAnswers.suggestionJson = """
            {
              "name": "Refactoring",
              "edition": "1st",
              "format": "paperback",
              "authors": ["Fowler Martin", "Beck Kent"]
            }
        """.trimIndent()
    }

    private fun givenTheScreenIsOpen() {
        screen = ActivityScenario.launch(MainActivity::class.java)
    }

    private fun givenABookHasBeenRecommended() {
        // The recommendation is made through the screen, as a user would make it.
        givenTheScreenIsOpen()
        onView(withHint("Email address")).perform(replaceText(enteredEmail), closeSoftKeyboard())
        onView(withText("Recommend book")).perform(click())
        waitUntilShown(withText(containsString("Refactoring")))
    }

    // When

    private fun whenTheLoadConfigButtonIsPressed() {
        onView(withText("Load Config")).perform(click())
    }

    // Then

    private fun thenTheReplyIsShown(reply: String) {
        waitUntilShown(withText(reply))
    }

    private fun thenAliceMessagerWasSentTheConfigRequest(expectedBody: String) {
        val sent = aliceMessager.takeRequest(5, TimeUnit.SECONDS) ?: error("AliceMessager was not sent a request")
        check(sent.path == "/AliceMessager/config") { "sent to ${sent.path}" }
        val body = sent.body.clone().readUtf8()
        check(body == expectedBody) { "sent body $body" }
    }

    /**
     * Checks the screen until the matcher passes, running the main looper between checks so that the
     * background response can update the screen.
     */
    private fun waitUntilShown(matcher: org.hamcrest.Matcher<android.view.View>) {
        val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10)
        while (true) {
            try {
                onView(matcher).check(matches(isDisplayed()))
                return
            } catch (failure: NoMatchingViewException) {
                if (System.currentTimeMillis() > deadline) throw failure
            } catch (failure: AssertionError) {
                if (System.currentTimeMillis() > deadline) throw failure
            }
            ShadowLooper.idleMainLooper()
            Thread.sleep(50)
        }
    }
}

// What AliceMessager answers. A POST with {"message":"Hello"} gets 200 with {"message":"World"}. Any other payload,
// including an empty one, gets 400 with {"message":"Is someone there?"}.
class AliceMessagerAnswers : Dispatcher() {

    companion object {
        const val HELLO = "{\"message\":\"Hello\"}"
        const val WORLD = "{\"message\":\"World\"}"
        const val IS_SOMEONE_THERE = "{\"message\":\"Is someone there?\"}"
    }

    override fun dispatch(request: RecordedRequest): MockResponse {
        val json = "application/json"
        return if (request.body.clone().readUtf8() == HELLO) {
            MockResponse().setResponseCode(200).setHeader("Content-Type", json).setBody(WORLD)
        } else {
            MockResponse().setResponseCode(400).setHeader("Content-Type", json).setBody(IS_SOMEONE_THERE)
        }
    }
}
