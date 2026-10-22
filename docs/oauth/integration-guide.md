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
| API | `https://onboarding-service.tuleva.ee/v1` |
| API metadata (RFC 9728) | `https://onboarding-service.tuleva.ee/.well-known/oauth-protected-resource` |
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

- A JWT valid for 10 minutes, signed by Tuleva with ES384, of type `at+jwt`. Send it as `Authorization: Bearer <access_token>`.
- It is for Tuleva's API: its audience (`aud`) is `https://onboarding-service.tuleva.ee/v1`.
- It stops working at once when the connection ends, even before its 10 minutes are over.
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

Either way, your next refresh gets `invalid_grant`, and an access token you still hold gets `401` on its next use.

## 5. Errors

| Where | Error | What to do |
|---|---|---|
| redirect | `access_denied` | The person said no. Do not retry on your own. |
| redirect | `invalid_scope` | Ask only for the scopes listed above. |
| token endpoint | `invalid_client` | Check the client assertion: key, `alg` (must be `ES384`), `aud`, `iat`, `exp`. If everything is right, your app may be suspended; contact us. |
| token endpoint | `invalid_grant` | The code expired or was used, or the connection has ended. Send the person through step 2 again. |
| API | `401`, `WWW-Authenticate: Bearer error="invalid_token"` | The access token expired: refresh it. If the refresh gets `invalid_grant`, the connection has ended. |
| API | `403` | The token has no scope for this endpoint, or the endpoint is not open to apps. Do not retry. |

## 6. Data

Apps read the person's data through Tuleva's own API, the one Tuleva's web app uses, limited to the scopes the person allowed:

| Endpoint | Scope | Returns |
|---|---|---|
| `GET https://onboarding-service.tuleva.ee/v1/savings-account-statement` | `balances:read` | the person's holding in Tuleva's savings fund |
| `GET https://onboarding-service.tuleva.ee/v1/transactions` | `transactions:read` | the person's savings fund transactions |

- For now, an app sees savings fund data only. II and III pillar data come later, once agreed.
- Every other API endpoint refuses an app's access token with `403`, whatever its scopes.
- Tuleva records every read for the person: which app, which connection, which endpoint and which scopes.
- Amounts are in euros. Times are UTC, in ISO 8601. Dates are calendar dates in Estonia.
- Send `Accept-Language: en` for English fund names. Without it, names are in Estonian.
- The examples below are real responses with made-up values. Expect new fields to appear over time and ignore the ones you do not know.

### Savings fund statement

`GET https://onboarding-service.tuleva.ee/v1/savings-account-statement` (scope `balances:read`)

`204` with no body when the person has never had a savings fund account. Otherwise `200` with the statement, which can show zero units, for example before the person's first payment arrives or after they have redeemed everything.

```json
{
  "fund": {
    "fundManager": { "name": "Tuleva" },
    "isin": "EE0000000000",
    "name": "Example Savings Fund",
    "managementFeeRate": 0.0016,
    "pillar": null,
    "ongoingChargesFigure": 0.0029,
    "status": "ACTIVE",
    "inceptionDate": "2025-09-18"
  },
  "value": 1234.56,
  "unavailableValue": 0.00,
  "currency": "EUR",
  "activeContributions": false,
  "contributions": 1000.00,
  "subtractions": -100.00,
  "profit": 334.56,
  "units": 100.12345
}
```

| Field | Meaning |
|---|---|
| `units` | fund units the person holds and can redeem |
| `value` | what `units` are worth at the latest net asset value |
| `unavailableValue` | what the units reserved for a pending redemption are worth |
| `contributions` | euros paid in for the person's units |
| `subtractions` | euros the person has redeemed, as a negative number |
| `profit` | `value` plus `unavailableValue`, minus `contributions` and `subtractions` |
| `fund.managementFeeRate`, `fund.ongoingChargesFigure` | yearly rates as fractions: `0.0016` is 0.16% |
| `fund.pillar`, `activeContributions` | used for pension funds only; `null` and `false` for the savings fund |

### Transactions

`GET https://onboarding-service.tuleva.ee/v1/transactions` (scope `transactions:read`)

`200` with a list, newest first. Only a person who has completed joining the savings fund gets their transactions. For anyone else the list is empty, even when the statement shows units.

```json
[
  {
    "id": "7d1a9f3e-2c4b-4e8a-9b6d-0f5e3a2c1b47",
    "amount": 400.00,
    "currency": "EUR",
    "time": "2026-10-14T09:00:00Z",
    "navDate": "2026-10-14",
    "priceCalculationDate": null,
    "settledTime": "2026-10-14T09:00:00Z",
    "applicationTime": null,
    "counterpartyIban": null,
    "isin": "EE0000000000",
    "type": "TRANSFER_IN",
    "units": 32.49391,
    "nav": null,
    "acquisitionCost": 350.00
  },
  {
    "id": "2b8e4d1c-6f3a-4a9b-8c7d-5e1f2a3b4c5d",
    "amount": -123.10,
    "currency": "EUR",
    "time": "2026-10-07T12:00:00Z",
    "navDate": "2026-10-06",
    "priceCalculationDate": "2026-10-07",
    "settledTime": "2026-10-09T08:00:00Z",
    "applicationTime": "2026-10-05T18:20:00Z",
    "counterpartyIban": null,
    "isin": "EE0000000000",
    "type": "SUBTRACTION",
    "units": 10.00000,
    "nav": 12.3100
  },
  {
    "id": "9c3f5a7b-1d2e-4f6a-8b9c-0d1e2f3a4b5c",
    "amount": 100.00,
    "currency": "EUR",
    "time": "2026-10-02T14:00:00Z",
    "navDate": "2026-10-01",
    "priceCalculationDate": "2026-10-02",
    "settledTime": "2026-10-02T14:00:00Z",
    "applicationTime": "2026-10-01T11:30:00Z",
    "counterpartyIban": null,
    "isin": "EE0000000000",
    "type": "CONTRIBUTION_CASH",
    "units": 8.13008,
    "nav": 12.3000
  }
]
```

| Field | Meaning |
|---|---|
| `id` | the transaction's stable ID |
| `type` | `CONTRIBUTION_CASH`: the person bought units with a payment. `SUBTRACTION`: the person redeemed units. `TRANSFER_IN`, `TRANSFER_OUT`: units moved from or to another person, for example as a gift or an inheritance. |
| `amount` | euros, negative when value leaves the person's holding |
| `units` | fund units, always positive; `type` says which way they moved |
| `nav` | the net asset value per unit the transaction used; `null` for a transfer that was not priced |
| `time` | when the transaction was booked |
| `navDate` | the date of the net asset value the transaction used. When the transaction has no net asset value date, the day it was booked, and `priceCalculationDate` is `null`. |
| `priceCalculationDate` | the day that net asset value was calculated, `null` when the transaction has no net asset value date |
| `settledTime` | when the money or the units settled |
| `applicationTime` | when the person's payment arrived or their redemption request was made, `null` when not known |
| `counterpartyIban` | always `null` for apps: Tuleva does not share bank account numbers with apps |
| `acquisitionCost` | on `TRANSFER_IN` only, and only when known: what the units cost the person for tax purposes, in euros |

## 7. Security expectations

- Keep refresh tokens encrypted at rest and out of logs, URLs and analytics.
- Check `state` on every redirect.
- Tell us within 24 hours if you suspect a leak of tokens or your private key.
- Tuleva can end any connection, or all of your app's connections at once, at any time.
