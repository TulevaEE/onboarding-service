# Onboarding-service

[![CircleCI](https://circleci.com/gh/TulevaEE/onboarding-service/tree/master.svg?style=shield)](https://circleci.com/gh/TulevaEE/onboarding-service/tree/master)
[![Known Vulnerabilities](https://snyk.io/test/github/TulevaEE/onboarding-service/badge.svg)](https://snyk.io/test/github/TulevaEE/onboarding-service)
[![codecov](https://codecov.io/gh/TulevaEE/onboarding-service/branch/master/graph/badge.svg)](https://codecov.io/gh/TulevaEE/onboarding-service)

## Architecture
![Tuleva Architecture](./reference/tuleva_architecture.png)

## Prerequisites

- JDK 8
- Groovy
- Git
- Gradle
- Lombok
- IntelliJ
- Docker

## Tech stack

**Database**

PostgreSQL

Running locally with Docker: `docker-compose up -d`

**Spring Profile**

IMPORTANT: Set your Spring active profile to `dev` - this will also run DB schema/dev data migration

**Backend**

Java 8, Spring Boot, Gradle, Spock for testing

Running locally: `./gradlew bootRun`

**Frontend**

React, ES6, scss, custom bootstrap, enzyme + jest for testing

**Exception Monitoring**

Sentry

**Analytics**

Google Analytics / Mixpanel

**Hosting**

AWS Elastic BeanStalk: EC2 and ELB

**Continuous Integration**

CircleCI

**Production Logs**

Papertrail

### API

Authentication: oAuth2 with Mobile-ID, ID-card and Smart-ID

[Swagger UI](https://onboarding-service.tuleva.ee/swagger-ui/)

[Postman API collection](reference/api.postman_collection) (outdated)


### Build pipeline

**Production:**
Merge GitHub pull request to master -> build in CircleCI -> auto-redeploy (if build is green)

### How to add new pension funds?
1. Add the new fund to the `funds` database table.

### Development notes

If you don't want to run epis-serivice,
then you can mock `TransferExchangeService.java`, which calls epis-service.

### Common Issues

`error="unsupported_grant_type", error_description="Unsupported grant type: mobile_id"`

Make sure you are running against the right backend environment (dev or prod).
- If you do `npm run develop` your `package.json` must proxy to `http://localhost:9000`
- If you do `npm run develop-production` your `package.json` must proxy to `https://onboarding-service.tuleva.ee`

### Known Issues

- Digital signing does not work in the dev environment. Use the production
 configuration to test it locally. See `DigiDocConfiguration.digiDocConfigDev()` and
  `mobile-id.hostUrl`, `smartid.relyingPartyUUID`, `smartid.relyingPartyName` config
   values in `application.yml` and change them to production values. Use VPN for testing.

### Caveats

When updating Spring Boot, sometimes you need to remove all of the existing access tokens from the
`oauth_access_token` database table. However, there's one special token granted for tuleva.ee which
allows it to fetch Fund NAV values and register new users. In order to generate a new token, you need to:
token by
```
curl --location --request POST 'https://pension.tuleva.ee/api/oauth/token' \
--header 'Authorization: Basic <base64 of client_id:client_secret>' \
--data-urlencode 'grant_type=client_credentials' \
--data-urlencode 'client_id=tuleva.ee'
```
and then [update the token values](https://github.com/TulevaEE/wordpress-theme/commit/1796c1ba7c926847ff0edb3b9f8a61e273d40018) in the WordPress Tuleva template.

### References

[hwcrypto.js](https://github.com/hwcrypto/hwcrypto.js)

[hwcrypto Sequence Diagram](https://github.com/hwcrypto/hwcrypto.js/wiki/SequenceDiagram)

[Test Authentication Methods](https://www.id.ee/en/article/testing-the-services/)

[Test Mobile ID](https://demo.sk.ee/MIDCertsReg/)

[Test ID Card](https://demo.sk.ee/upload_cert/)

[Test Smart ID](https://github.com/SK-EID/smart-id-documentation/wiki/Smart-ID-demo)
