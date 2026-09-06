package com.trevorism.gcloud

import io.cucumber.groovy.EN
import io.cucumber.groovy.Hooks

this.metaClass.mixin(Hooks)
this.metaClass.mixin(EN)

String sessionBaseUrl = System.getenv("ACCEPTANCE_BASE_URL") ?: "https://billing.trade.trevorism.com"

int connectTimeoutMillis = 10_000
int readTimeoutMillis = 30_000

def sessionStatus
def sessionLocation
def sessionBody

def openSessionRoute = { String path, String method ->
    HttpURLConnection connection = new URL("${sessionBaseUrl}/${path}").openConnection() as HttpURLConnection
    connection.instanceFollowRedirects = false
    connection.requestMethod = method
    connection.connectTimeout = connectTimeoutMillis
    connection.readTimeout = readTimeoutMillis
    return connection
}

When(~/^I ask this app who is signed in$/) { ->
    HttpURLConnection connection = openSessionRoute("api/auth/session", "GET")
    sessionStatus = connection.responseCode
    sessionBody = sessionStatus < 400 ? connection.inputStream.text : null
    connection.disconnect()
}

Then(~/^this app reports that nobody is signed in$/) { ->
    assert sessionStatus == 200, "expected 200 but got ${sessionStatus}"
    assert sessionBody?.contains('"authenticated":false'), "unexpected session body: ${sessionBody}"
}

When(~/^I start the login handoff$/) { ->
    HttpURLConnection connection = openSessionRoute("api/auth/login", "GET")
    sessionStatus = connection.responseCode
    sessionLocation = connection.getHeaderField("Location")
    connection.disconnect()
}

Then(~/^this app sends me to the login application with a callback on its own host$/) { ->
    assert sessionStatus == 302, "expected a 302 but got ${sessionStatus}"
    assert sessionLocation?.startsWith("https://login.auth.trevorism.com/authorize"), sessionLocation
    assert sessionLocation.contains(URLEncoder.encode("${sessionBaseUrl}/api/auth/callback", "UTF-8")), sessionLocation
    assert sessionLocation.contains("state="), sessionLocation
}

/**
 * Posts with no body under a caller-chosen content type. A browser labels a bodyless post
 * form-urlencoded, and an endpoint that only consumes JSON answers 415 before the handler
 * runs, which a check that always sends JSON cannot see.
 */
When(~/^I POST "(.*)" as "(.*)"$/) { String path, String contentType ->
    HttpURLConnection connection = openSessionRoute(path, "POST")
    connection.setRequestProperty("Content-Type", contentType)
    connection.setRequestProperty("Content-Length", "0")
    connection.doOutput = true
    connection.outputStream.withCloseable { it.write(new byte[0]) }
    sessionStatus = connection.responseCode
    connection.disconnect()
}

When(~/^I GET "(.*)" anonymously and note the status$/) { String path ->
    HttpURLConnection connection = openSessionRoute(path, "GET")
    sessionStatus = connection.responseCode
    connection.disconnect()
}

Then(~/^this app answers (\d+)$/) { Integer expected ->
    assert sessionStatus == expected, "expected ${expected} but got ${sessionStatus}"
}
