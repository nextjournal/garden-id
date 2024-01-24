# nextjournal.garden-id

Simplified authentication for [application.garden](https://application.garden) based on OpenID Connect.

## Usage

Wrap your Ring app with `ring.middleware.session/wrap-session` and `nextjournal.garden-id/wrap-auth`.

Redirecting to the path in `nextjournal.garden-id/login-uri` will send the user to a login page. Upon successful login it redirects to "/" and user data is stored in the session.

In local development authentication is mocked and you can impersonate arbitrary users.

## Authorization

You can configure authorization by passing a map as the second argument to `nextjournal.garden-id/wrap-auth`.

### Github

To only allow members of a certain Github organization or team to access your application, use:

`(nextjournal.garden-id/wrap-auth my-app {:github [["organization"]... ["organization" "team"]...]})`

You need a valid Github API token in the environment variable `GITHUB_API_TOKEN` that is scoped to read the organization members.

Use an [application.garden secret](https://docs.apps.garden#secrets) to set this.

### Apple ID

To only allow login with Apple ID, use:

`(nextjournal.garden-id/wrap-auth my-app {:apple []})`
