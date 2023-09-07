# nextjournal.garden-id

Provides helpers to work with the clerk.garden OpenID Connect provider.
Wrap your Ring app using `(garden-id/wrap-auth <app>)`, and ensure you also
use `ring.middleware.session/wrap-session`.

Redirecting to "/login" will send the user to a login page; upon
successful login it redirects to "/" and user data is stored in the
session.

The url "/callback" is used internally and intercepted before your app,
do not use it.
