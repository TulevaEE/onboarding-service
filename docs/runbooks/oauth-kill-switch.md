# OAuth client kill switch

Cuts a connected app off at once, without a deploy: the client can no longer authenticate, every grant it holds is revoked, and its access tokens stop working on their next use.

Run it as one transaction against production:

```
psql --single-transaction -f docs/runbooks/oauth-kill-switch-portfellow.sql
```

`OAuthAuthorizationFlowIntegrationTest` runs this exact file, so a schema change that breaks it fails the build.

The revoked grants stay revoked. To let the client back in, delete its row from `oauth_client_suspension`; people then connect it again from scratch.

To cut off another client, copy the file and change the client id.
