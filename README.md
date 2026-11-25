# Deployagram Android AppA Robolectric JUnit 4 Proxy Demo

This demo shows how to use the **Deployagram HTTP Proxy** in the tests of an Android app. The app is AppA, a small
Android app with one screen. Its tests run on the JVM with **Robolectric** and **JUnit 4**, so no emulator is needed.

It is one of the [Deployagram](https://deployagram.com) demos. The app itself is not about Deployagram. The same app
is tested without the Proxy in
[`deployagram-android-appA-robolectric-junit4-demo`](../deployagram-android-appA-robolectric-junit4-demo), where the
doubles record their own traffic.

## What the app does

AppA has one screen. The user types an email address and presses **Recommend book**. The app asks BookOverflow for a
book suggestion, and shows the answer. A second button, **Load Config**, calls AliceMessager.

| Situation                                        | What the screen shows                                                            |
|--------------------------------------------------|----------------------------------------------------------------------------------|
| BookOverflow suggests a book                     | The book's name, edition, format and authors                                     |
| Email is blank, and BookOverflow refuses (400)   | `You must enter a valid email!`                                                  |
| Load Config, after a suggestion                  | AliceMessager's reply, `World` for `Hello` (sent once a book is recommended)     |
| Load Config, with no suggestion yet              | AliceMessager's reply, `Is someone there?` for an empty payload                  |

The tests never send an HTTP request themselves. They type an email and press the button, and AppA makes the
requests.

## How the tests use the Proxy

AppA is pointed at the Proxy, not at its services. The Proxy forwards each request to the service's double, which
runs on the test machine, and Deployagram records the exchange.

```
  test ──► AppA ──► Deployagram HTTP Proxy (Docker) ──► BookOverflow double (MockWebServer, port 8091)
                                 │
                                 └──► AliceMessager double (MockWebServer, port 8092)

  Collector (Docker), started with the Proxy, records each exchange under the test's name
```

Each test class declares the routes, and the environment that the Proxy needs:

* `@Deployagram(startEnvironment = true, proxyPort = 9080, shareHostPorts = [...])` starts the Collector and the Proxy
  in Docker, and shares the double ports with the containers.
* `@DeployagramConfig(...)` gives the Proxy one route per service. For example, `proxy.proxiedApps.BookOverflow/suggestion`
  is where the Proxy forwards BookOverflow's calls.
* `DeployagramRule` is a JUnit 4 rule. It posts the test class's routes to the Proxy before each test, and ends the
  test's sequence diagram in the Collector after it.

The ports and the Proxy's port are in [`ProxyPorts.kt`](app/src/test/java/com/deployagram/demo/appa/ProxyPorts.kt).

### Starting the environment under Robolectric

Robolectric does not run a JUnit `@ClassRule`, so `DeployagramEnvironmentRule` is never applied under Robolectric, and
the Proxy never starts. Each test class therefore applies the rule itself, in a `@BeforeClass` method, using the class's
own annotations:

```kotlin
val description = Description.createSuiteDescription(RecommendBookScreenTest::class.java)
DeployagramEnvironmentRule().apply(object : Statement() {
    override fun evaluate() {}
}, description).evaluate()
```

Both test classes do this. The environment starts once for the JVM, so the second class finds it already running.

## Tech

* Kotlin, Android Gradle Plugin 8.9.0, Gradle 8.12 (checked-in wrapper)
* Java 17 to build the app, and Java 21 to run the tests (Robolectric's SDK 36 runtime needs Java 21)
* Min SDK 26, compile and target SDK 36
* Robolectric 4.18.1, JUnit 4.13.2, Espresso 3.6.1, AndroidX Test core 1.6.1
* OkHttp 5.3.2 in the app, MockWebServer 5.3.2 for the doubles
* Deployagram: `deployagram-junit4` 1.2.0 and `deployagram-environment` 1.1.0, from Maven Central

## Project layout

```
app/src/main/java/com/deployagram/demo/appa/
    MainActivity.kt          the one screen: email field, buttons, result
    BookOverflowClient.kt    GET /BookOverflow/suggestion, with OkHttp
    BookSuggestion.kt        the suggestion, and how it is shown
    BookOverflowConfig.kt    where BookOverflow is (the tests point it at the Proxy)
    AliceMessagerClient.kt   POST /AliceMessager/config, with OkHttp
    AliceMessagerConfig.kt   where AliceMessager is (the tests point it at the Proxy)
app/src/test/java/com/deployagram/demo/appa/
    ProxyPorts.kt            the Proxy's port, and the double ports
    RecommendBookScreenTest.kt   the two tests of Recommend book, through the Proxy
    LoadConfigButtonTest.kt      the two tests of Load Config, through the Proxy
```

## Run the tests

The tests need three things before they run:

1. **Docker**, running. The Collector and the Proxy run in containers.
2. **A JDK 21** that Gradle can find. Java 17 is enough to build the app, but the tests run on Java 21.
3. **Deployagram settings** in the environment:
   * the licence, from `DEPLOYAGRAM_LICENSE` (the licence file's contents), or from a `.deployagram` file in `app/`;
   * the cloud settings `DEPLOYAGRAM_LICENSE_KEY`, `DEPLOYAGRAM_CLIENT_ID`, `DEPLOYAGRAM_CLOUD_USERNAME` and
     `DEPLOYAGRAM_CLOUD_PASSWORD`.

Without the licence the Proxy cannot start, and the tests fail with `NoSuchFileException` for `.deployagram`, or with
`The Deployagram HTTP Proxy is not running`. If `DEPLOYAGRAM_LICENSE` is not set, the `.deployagram` file must be in
`app/`, because Gradle runs the tests from that folder, not from the repository root.

Put the settings in a `.env` file in the repository root, which is git-ignored, and load it before running Gradle:

```
DEPLOYAGRAM_LICENSE_KEY=...
DEPLOYAGRAM_CLIENT_ID=...
DEPLOYAGRAM_CLOUD_USERNAME=...
DEPLOYAGRAM_CLOUD_PASSWORD=...
```

```bash
set -a && . ./.env && set +a
export DEPLOYAGRAM_LICENSE="$(cat ./.deployagram)"
./gradlew clean testDebugUnitTest
```

The `.deployagram` file is git-ignored too, so neither file is ever committed. Gradle reads `DEPLOYAGRAM_LICENSE`, so
the `export` line is enough; the file does not also need to be in `app/` when that variable is set.

The test report is in `app/build/reports/tests/testDebugUnitTest/index.html`. The test classes start the Collector and
the Proxy in Docker, so the run takes longer than a plain JVM test run.

### What the tests check

`RecommendBookScreenTest` has two tests:

* **Pressing Recommend book asks BookOverflow for a suggestion and shows it.** The test checks that BookOverflow was
  asked for the email the test typed, through the Proxy, and that the screen shows the book.
* **Pressing Recommend book with no email asks BookOverflow, which refuses, and asks for a valid email.** The double
  refuses a request with no email, with a 400. The test checks the request, and that the screen shows
  `You must enter a valid email!`.

`LoadConfigButtonTest` has two tests:

* **Pressing Load Config after a recommendation sends `Hello` to AliceMessager and shows `World`.**
* **Pressing Load Config with no recommendation sends an empty payload and shows AliceMessager's reply.**

Each test sets up the doubles in a **given** step, presses the button in a **when** step, and checks the result in
**then** steps.

## Deployagram names

The caller and callee are named as in the Robolectric recorder demo and the pipeline: `AliceA` calls `ORG_ctm_01m1j08sx96792d2tysabvyadp:BookOverflow` and `AliceMessager`. Each test's
entries in the Collector are under its own marker, `<test class>.<test name>`.
