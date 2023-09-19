# nextjournal.garden-id

Provides helpers to work with the clerk.garden OpenID Connect provider.
Wrap your Ring app using `(garden-id/wrap-auth <app>)`, and ensure you also
use `ring.middleware.session/wrap-session`.

Redirecting to "/login" will send the user to a login page; upon
successful login it redirects to "/" and user data is stored in the
session.

The url "/callback" is used internally and intercepted before your app,
do not use it.

## Additional restrictions

Pass a map as second argument to `garden-id/wrap-auth`.
Currently supported keys are:

`{:github [["organization"]... ["organization" "team"]...]}`:
restrict access to members of an organization or a team thereof.
You need a valid Github API token in the environment variable
`GITHUB_API_TOKEN` that is scoped to read the organization members.
(Use a Garden secret to set this!)
