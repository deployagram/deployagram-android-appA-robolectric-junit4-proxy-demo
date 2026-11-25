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
import com.deployagram.junit4.Deployagram
import com.deployagram.junit4.DeployagramConfig
import com.deployagram.junit4.DeployagramConfigEntry
import com.deployagram.junit4.DeployagramEnvironmentRule
import com.deployagram.junit4.DeployagramRule
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.hamcrest.CoreMatchers.containsString
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper
import java.net.InetAddress
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Tests AppA's "Recommend book" screen on the JVM, with Robolectric. No emulator is needed.
 *
 * The test never sends an HTTP request itself. It types an email and presses the button, and AppA makes the
 * request to BookOverflow. AppA sends it to the Deployagram HTTP Proxy, which forwards it to BookOverflow, a
 * MockWebServer double. The test checks what the double received and what the screen shows.
 */
@RunWith(RobolectricTestRunner::class)
@Deployagram(startEnvironment = true, proxyPort = PROXY_PORT, shareHostPorts = [BOOK_OVERFLOW_PORT, ALICE_MESSAGER_PORT])
@DeployagramConfig(
    DeployagramConfigEntry(key = "proxy.namesOfSourceApps.BookOverflow/suggestion", value = "AliceA"),
    DeployagramConfigEntry(key = "proxy.proxiedAppNames.BookOverflow/suggestion", value = "ORG_ctm_01m1j08sx96792d2tysabvyadp:BookOverflow"),
    DeployagramConfigEntry(
        key = "proxy.proxiedApps.BookOverflow/suggestion",
        value = "http://host.testcontainers.internal:8091/BookOverflow/suggestion",
    ),
    DeployagramConfigEntry(key = "proxy.namesOfSourceApps.AliceMessager/config", value = "AliceA"),
    DeployagramConfigEntry(key = "proxy.proxiedAppNames.AliceMessager/config", value = "AliceMessager"),
    DeployagramConfigEntry(
        key = "proxy.proxiedApps.AliceMessager/config",
        value = "http://host.testcontainers.internal:8092/AliceMessager/config",
    ),
)
class RecommendBookScreenTest {

    companion object {
        // Robolectric does not run the class rule, so the rule is applied here, to the class's own annotations.
        @JvmStatic
        @BeforeClass
        fun startDeployagramEnvironment() {
            val description = Description.createSuiteDescription(RecommendBookScreenTest::class.java)
            DeployagramEnvironmentRule().apply(object : Statement() {
                override fun evaluate() {}
            }, description).evaluate()
        }
    }

    @get:Rule
    val deployagram = DeployagramRule()

    private val defaultBookOverflowUrl = BookOverflowConfig.baseUrl
    private lateinit var bookOverflow: MockWebServer
    private lateinit var bookOverflowAnswers: SuggestionAnswers
    private lateinit var enteredEmail: String
    private var screen: ActivityScenario<MainActivity>? = null

    @Before
    fun startBookOverflow() {
        bookOverflow = MockWebServer()
        bookOverflowAnswers = SuggestionAnswers()
        enteredEmail = "reader-${UUID.randomUUID()}@example.com"
        bookOverflow.dispatcher = bookOverflowAnswers
        bookOverflow.start(InetAddress.getByName("0.0.0.0"), BOOK_OVERFLOW_PORT)
        // AppA calls the Proxy, which forwards to the double.
        BookOverflowConfig.baseUrl = "http://localhost:$PROXY_PORT"
    }

    @After
    fun stopBookOverflow() {
        screen?.close()
        screen = null
        bookOverflow.shutdown()
        BookOverflowConfig.baseUrl = defaultBookOverflowUrl
    }

    @Test
    fun pressingRecommendBookAsksBookOverflowForASuggestionAndShowsIt() {
        givenBookOverflowHasASuggestion()

        whenTheRecommendBookButtonIsPressed()

        thenTheSuggestionIsShown()
        thenBookOverflowWasAskedForTheEnteredEmail()
    }

    @Test
    fun pressingRecommendBookWithNoEmailAsksBookOverflowWhichRefusesAndAsksForAValidEmail() {
        givenBookOverflowRefusesARequestWithNoEmail()
        givenNoEmailIsEntered()

        whenTheRecommendBookButtonIsPressed()

        thenTheInvalidEmailPromptIsShown()
        thenBookOverflowWasCalledWithoutAnEmailAddress()
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

    private fun givenBookOverflowRefusesARequestWithNoEmail() {
        bookOverflowAnswers.refusesRequestsWithNoEmail = true
    }

    private fun givenNoEmailIsEntered() {
        enteredEmail = ""
    }

    // When

    private fun whenTheRecommendBookButtonIsPressed() {
        screen = ActivityScenario.launch(MainActivity::class.java)
        onView(withHint("Email address"))
            .perform(replaceText(enteredEmail), closeSoftKeyboard())
        onView(withText("Recommend book"))
            .perform(click())
    }

    // Then

    private fun thenTheSuggestionIsShown() {
        // AppA makes the request in the background, so the screen is checked until the result appears.
        waitUntilShown(withText(containsString("Refactoring")))
    }

    private fun thenTheInvalidEmailPromptIsShown() {
        waitUntilShown(withText("You must enter a valid email!"))
    }

    private fun thenBookOverflowWasAskedForTheEnteredEmail() {
        val request = bookOverflow.takeRequest(5, TimeUnit.SECONDS)
            ?: error("BookOverflow was not asked for a suggestion for \"$enteredEmail\"")
        check(request.requestUrl?.encodedPath == "/BookOverflow/suggestion") { "asked at ${request.requestUrl}" }
        check(request.requestUrl?.queryParameter("email") == enteredEmail) { "asked for ${request.requestUrl}" }
    }

    private fun thenBookOverflowWasCalledWithoutAnEmailAddress() {
        val request = bookOverflow.takeRequest(5, TimeUnit.SECONDS)
            ?: error("BookOverflow was not asked for a suggestion")
        check(request.requestUrl?.encodedPath == "/BookOverflow/suggestion") { "asked at ${request.requestUrl}" }
        check(request.requestUrl?.queryParameter("email") == "") { "asked for ${request.requestUrl}" }
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

// What BookOverflow answers. A test sets what it wants with the given steps.
// Until then, BookOverflow has no suggestion and answers 404.
class SuggestionAnswers : Dispatcher() {
    @Volatile
    var suggestionJson: String? = null

    // When true, a request without an email is refused with 400, as BookOverflow refuses it.
    @Volatile
    var refusesRequestsWithNoEmail = false

    override fun dispatch(request: RecordedRequest): MockResponse {
        if (refusesRequestsWithNoEmail && request.requestUrl?.queryParameter("email").isNullOrBlank()) {
            return MockResponse().setResponseCode(400)
        }
        val json = suggestionJson ?: return MockResponse().setResponseCode(404)
        return MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(json)
    }
}
