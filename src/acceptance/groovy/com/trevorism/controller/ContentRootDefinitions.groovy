package com.trevorism.controller

/**
 * @author tbrooks
 */

this.metaClass.mixin(io.cucumber.groovy.Hooks)
this.metaClass.mixin(io.cucumber.groovy.EN)

String baseUrl = System.getenv("ACCEPTANCE_BASE_URL") ?: "https://billing.trade.trevorism.com"

def contextRootContent
def pingContent

Given(~/^the application is alive$/) { ->
    try {
        new URL("${baseUrl}/api/ping").text
    }
    catch (Exception ignored) {
        Thread.sleep(10000)
        new URL("${baseUrl}/api/ping").text
    }
}

When(~/^I navigate to the application root$/) { ->
    contextRootContent = new URL("${baseUrl}/api").text
}

Then(~/^a link to the help page is displayed$/) { ->
    assert contextRootContent
    assert contextRootContent.contains("/api/help")
}

When(~/^I ping the application$/) { ->
    pingContent = new URL("${baseUrl}/api/ping").text
}

Then(~/^pong is returned, to indicate the service is alive$/) { ->
    assert pingContent == "pong"
}
