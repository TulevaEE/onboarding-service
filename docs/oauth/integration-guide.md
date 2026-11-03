# Connecting to Tuleva with OAuth 2

This guide is for an app that reads a person's Tuleva data with their permission. Tuleva is the OAuth 2 authorization server. The app uses the authorization code flow with PKCE and authenticates itself with a signed JWT (`private_key_jwt`). There is no shared client secret.

## At a glance

| | |
|---|---|
| Issuer | `https://onboarding-service.tuleva.ee` |
| Metadata | `https://onboarding-service.tuleva.ee/.well-known/oauth-authorization-server` |
| Authorization endpoint | `https://onboarding-service.tuleva.ee/oauth2/authorize` |
| Token endpoint | `https://onboarding-service.tuleva.ee/oauth2/token` |
| Revocation endpoint | `https://onboarding-service.tuleva.ee/oauth2/revoke` |
| Token signing keys (JWKS) | `https://onboarding-service.tuleva.ee/oauth2/jwks` |
| Grant types | `authorization_code`, `refresh_token` |
| Client authentication | `private_key_jwt`, ES384 (ECDSA on P-384) |
| PKCE | required, `S256` |
| Scopes | `balances:read`, `transactions:read` |

## 1. Setup

### Generate your key pair

Use an elliptic curve key on NIST P-384, the curve Estonian ID cards use. Tuleva accepts no other key type. Generate it on a machine you trust:

```sh
openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-384 -out tuleva-private-key.pem
openssl pkey -in tuleva-private-key.pem -pubout -out tuleva-public-key.pem
```

- Send us `tuleva-public-key.pem` only. It is not secret.
- Keep `tuleva-private-key.pem` in your secrets manager. Never commit it, email it, or log it. Tuleva never needs to see it.
- To rotate, generate a new pair and send us the new public key. We accept two keys at once, so you can switch without downtime, then tell us to remove the old one.
- If you think the private key leaked, tell us at once. We will remove it and end every connection your app holds.

### Send us

- your public key (`tuleva-public-key.pem`)
- your redirect URIs: exact `https` URLs, no wildcards
- the app name to show people on the consent screen
- a security contact we can reach if something goes wrong

### We send you

- your `client_id`

## 2. Ask the person for access

Send the person's browser to the authorization endpoint:

```
GET https://onboarding-service.tuleva.ee/oauth2/authorize
  ?response_type=code
  &client_id=<client_id>
  &redirect_uri=<one of your registered redirect URIs>
  &scope=balances:read%20transactions:read
  &state=<random value you check on return>
  &code_challenge=<BASE64URL(SHA256(code_verifier))>
  &code_challenge_method=S256
```

- `redirect_uri` and `scope` are required. Ask only for the scopes you need. The person approves all requested scopes or none.
- Tuleva shows its own page, where the person logs in again with Smart-ID, Mobile-ID or ID-card and then allows or denies access. An existing Tuleva session never counts.
- The person must finish in the same browser within 10 minutes.
- On phones, open the authorization URL in the system browser (`ASWebAuthenticationSession` on iOS, Custom Tabs on Android), never in an embedded web view. Smart-ID and Mobile-ID logins need it.

The browser comes back to your redirect URI:

- allowed: `?code=<code>&state=<state>`
- denied: `?error=access_denied&state=<state>`

If the client ID or redirect URI is wrong, Tuleva shows an error and never redirects.

## 3. Exchange the code for tokens

The code is valid for one minute and only once.

```
POST https://onboarding-service.tuleva.ee/oauth2/token
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code
&code=<code>
&redirect_uri=<the same redirect_uri>
&code_verifier=<code_verifier>
&client_id=<client_id>
&client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer
&client_assertion=<signed JWT, see below>
```

The response:

```json
{
  "access_token": "eyJ...",
  "refresh_token": "...",
  "token_type": "Bearer",
  "expires_in": 600,
  "scope": "balances:read transactions:read"
}
```

### The client assertion

A JWT signed with your private key, created fresh for every request to the token and revocation endpoints:

| Claim | Value |
|---|---|
| header `alg` | `ES384` |
| `iss` | your `client_id` |
| `sub` | your `client_id` |
| `aud` | `https://onboarding-service.tuleva.ee` |
| `iat` | now |
| `exp` | at most 60 seconds after `iat` |
| `jti` | a new random value each time |

An example in Node.js with the `jose` library:

```js
import { SignJWT, importPKCS8 } from 'jose';
import { randomUUID } from 'node:crypto';

const privateKey = await importPKCS8(process.env.TULEVA_PRIVATE_KEY, 'ES384');

export async function clientAssertion(clientId) {
  const now = Math.floor(Date.now() / 1000);
  return new SignJWT({})
    .setProtectedHeader({ alg: 'ES384' })
    .setIssuer(clientId)
    .setSubject(clientId)
    .setAudience('https://onboarding-service.tuleva.ee')
    .setIssuedAt(now)
    .setExpirationTime(now + 60)
    .setJti(randomUUID())
    .sign(privateKey);
}
```

Keep your server clock synchronized: an assertion issued more than a minute in the future is refused.

## 4. Tokens

### Access token

- A JWT valid for 10 minutes, signed by Tuleva with ES384. Send it as `Authorization: Bearer <access_token>`.
- `sub` identifies the person. It is stable for your app and different for every other app; you never receive the person's personal identification code. Use `sub` as your user ID for Tuleva.
- Do not depend on other claims.

### Refresh token

```
POST https://onboarding-service.tuleva.ee/oauth2/token
Content-Type: application/x-www-form-urlencoded

grant_type=refresh_token
&refresh_token=<refresh_token>
&client_id=<client_id>
&client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer
&client_assertion=<signed JWT>
```

- Every refresh returns a new refresh token, and the old one stops working.
- Save the new refresh token before you use the new access token, and save it atomically. If you send an old refresh token again, even as a retry after a timeout, Tuleva treats it as stolen and ends the whole connection. The person then has to connect again.
- Refresh one connection from one worker at a time.
- A connection ends after 30 days without a refresh, and in any case 180 days after the person allowed it. After that the person has to connect again.

### Ending a connection

When the person disconnects in your app, revoke the refresh token:

```
POST https://onboarding-service.tuleva.ee/oauth2/revoke
Content-Type: application/x-www-form-urlencoded

token=<refresh_token>
&token_type_hint=refresh_token
&client_id=<client_id>
&client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer
&client_assertion=<signed JWT>
```

A connection can also end on Tuleva's side:

- the person revokes it on Tuleva's Security page
- Tuleva revokes it, for example after a suspected leak

Either way, your next refresh gets `invalid_grant`.

## 5. Errors

| Where | Error | What to do |
|---|---|---|
| redirect | `access_denied` | The person said no. Do not retry on your own. |
| redirect | `invalid_scope` | Ask only for the scopes listed above. |
| token endpoint | `invalid_client` | Check the client assertion: key, `alg` (must be `ES384`), `aud`, `iat`, `exp`. If everything is right, your app may be suspended; contact us. |
| token endpoint | `invalid_grant` | The code expired or was used, or the connection has ended. Send the person through step 2 again. |

## 6. Data

Draft. The endpoints and fields below are a proposal to agree on before we build them.

All data endpoints need an access token with the matching scope. Amounts are in euros.

### `GET https://onboarding-service.tuleva.ee/connect/v1/balances` (scope `balances:read`)

```json
{
  "holdings": [
    {
      "isin": "EE0000000000",
      "fundName": "Example Pension Fund",
      "units": 1234.567,
      "unitPrice": 1.2345,
      "value": 1524.17,
      "currency": "EUR",
      "priceDate": "2026-10-19"
    }
  ]
}
```

### `GET https://onboarding-service.tuleva.ee/connect/v1/transactions?from=2026-01-01&to=2026-10-20` (scope `transactions:read`)

```json
{
  "transactions": [
    {
      "id": "b3a1f0c2-...",
      "date": "2026-10-15",
      "isin": "EE0000000000",
      "fundName": "Example Pension Fund",
      "type": "CONTRIBUTION",
      "units": 10.123,
      "unitPrice": 1.2301,
      "amount": 12.45,
      "currency": "EUR"
    }
  ]
}
```

## 7. Security expectations

- Keep refresh tokens encrypted at rest and out of logs, URLs and analytics.
- Check `state` on every redirect.
- Tell us within 24 hours if you suspect a leak of tokens or your private key.
- Tuleva can end any connection, or all of your app's connections at once, at any time.
