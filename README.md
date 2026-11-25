# Deployagram Android AppA Robolectric JUnit 4 Demo

The same small Android app as [`deployagram-android-appA-kotest-demo`](../deployagram-android-appA-kotest-demo): AppA has one
screen. The user types an email and presses **Recommend book**, and the app asks BookOverflow for a book suggestion.

This version tests the screen on the JVM, with **Robolectric** and **JUnit 4**. It needs no emulator, so it runs on any
machine or CI runner that has Java 21. The Kotest version runs on an emulator.

## What it does

AppA calls `GET /BookOverflow/suggestion?email=<email>` and shows the answer:

| Situation                                      | What the screen shows                                        |
|------------------------------------------------|--------------------------------------------------------------|
| BookOverflow suggests a book                   | The book's name, edition, format and authors                 |
| Email is blank, and BookOverflow refuses (400) | `You must enter a valid email!`                              |
| Any other failure                              | `Sorry, no suggestion right now (<reason>)`                  |

## What it is for

* drive an Android screen from a JVM test with **Espresso** and **Robolectric**;
* stand in for BookOverflow with a **MockWebServer** double, and check what the app sent;
* write the test in plain **JUnit 4**, the default for Android.

The test never sends an HTTP request. It types an email and presses the button, and AppA makes the request.

## Tech

* Kotlin, Android Gradle Plugin 8.9.0, Gradle 8.12 (checked-in wrapper)
* Java 17 to build the app, and Java 21 to run the tests (Robolectric's Android 16 runtime needs Java 21)
* Min SDK 26, compile and target SDK 36
* Robolectric 4.18.1, JUnit 4.13.2, Espresso 3.6.1, AndroidX Test core 1.6.1
* OkHttp 5.3.2 in the app, MockWebServer 5.3.2 in the tests

## Run the tests

```bash
./gradlew testDebugUnitTest
```

The test report is in `app/build/reports/tests/testDebugUnitTest/index.html`.

## Project layout

```
app/src/main/java/com/deployagram/demo/appa/
    MainActivity.kt          the one screen: email field, button, result
    BookOverflowClient.kt    GET /BookOverflow/suggestion, with OkHttp
    BookSuggestion.kt        the suggestion, and how it is shown
    BookOverflowConfig.kt    where BookOverflow is (the test changes it)
app/src/test/java/com/deployagram/demo/appa/
    RecommendBookScreenTest.kt   the two JVM tests, and the BookOverflow double
```
