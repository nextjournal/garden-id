# nextjournal.garden-id

Simplified authentication for [application.garden](https://application.garden) based on OpenID Connect.

## Installation

Garden ID is [hosted on GitHub](https://github.com/nextjournal/garden-id) so you can simply add it as a git dependency to your `deps.edn`:

```clojure {:nextjournal.clerk/code-listing true}
{io.github.nextjournal/garden-id {:git/sha "<latest-sha>"}}
```

## Usage

Wrap your Ring app with `ring.middleware.session/wrap-session` and `nextjournal.garden-id/wrap-auth`.

Redirecting to the path in `nextjournal.garden-id/login-uri` will send the user to a login page. Upon successful login it redirects to "/" and user data is stored in the session.

In local development authentication is mocked and you can impersonate arbitrary users.

## Authorization

You can configure authorization by passing a map as the second argument to `nextjournal.garden-id/wrap-auth`.

### Github

To only allow certain Github users, members of a certain Github
organization or team to access your application, use:

`(nextjournal.garden-id/wrap-auth my-app {:github [RESTRICTIONS...]})`

Possible restrictions are:

* `{:login "githubhandle"}`: the user `githubhandle`
* `{:id 1234567}`: the user with the Github ID 1234567
* `{:login ifn}`: call ifn with the GitHub login handle, pass if returns true
* `{:id ifn}`: call ifn with the GitHub id, pass if returns true
* `{:organization "myorg"}`: members of the organization `myorg`.
* `{:organization "myorg" :team "myteam"}`: members of the team `myteam` of the organization `myorg`.

The user is permitted if they pass any listed restriction.

You need a valid Github API token in the environment variable `GITHUB_API_TOKEN` that is scoped to read the organization members.

Use an [application.garden secret](https://docs.apps.garden#secrets) to set this.

### Apple ID

To only allow login with Apple ID, use:

`(nextjournal.garden-id/wrap-auth my-app {:apple []})`

## Example

See [example](https://github.com/nextjournal/garden-id/tree/main/example) for an example application.
