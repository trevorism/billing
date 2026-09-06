Feature: Browser session
  The header bar reads the session from this app's own host, so these routes must exist here

  Scenario: An anonymous visitor has no session
    When I ask this app who is signed in
    Then this app reports that nobody is signed in

  Scenario: Signing in starts the one time code handoff
    When I start the login handoff
    Then this app sends me to the login application with a callback on its own host

  Scenario: Logout accepts the content type a browser actually sends
    When I POST "api/auth/logout" as "application/x-www-form-urlencoded"
    Then this app answers 200

  Scenario: Refreshing without a token is rejected
    When I POST "api/auth/refresh" as "application/x-www-form-urlencoded"
    Then this app answers 401
