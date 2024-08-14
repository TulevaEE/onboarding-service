# Onboarding-service

[![CircleCI](https://circleci.com/gh/TulevaEE/onboarding-service/tree/master.svg?style=shield)](https://circleci.com/gh/TulevaEE/onboarding-service/tree/master)
[![Known Vulnerabilities](https://snyk.io/test/github/TulevaEE/onboarding-service/badge.svg)](https://snyk.io/test/github/TulevaEE/onboarding-service)
[![codecov](https://codecov.io/gh/TulevaEE/onboarding-service/branch/master/graph/badge.svg)](https://codecov.io/gh/TulevaEE/onboarding-service)

## Architecture
![Tuleva Architecture](./reference/tuleva_architecture.png)

## Prerequisites

- JDK 21
- Groovy
- Git
- Gradle
- Lombok
- IntelliJ
- [AWS Toolkit for IntelliJ](https://aws.amazon.com/intellij/)
- Docker

## Tech stack

**Database**

PostgreSQL

Running locally with Docker: `docker compose up database -d`

**Spring Profile**

IMPORTANT: Set your Spring active profile to `dev` - this will also run DB schema/dev data migration

**Backend**

Java 21, Spring Boot, Gradle, Spock for testing

Running locally: `./gradlew bootRun`

**Frontend**

React, TypeScript, scss, custom bootstrap, react-testing-library

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

Code style:
[Java](https://github.com/google/styleguide/blob/gh-pages/intellij-java-google-style.xml),
[Kotlin](https://github.com/pinterest/ktlint#-with-intellij-idea)

If you don't want to run epis-service,
then you can use `mock` spring profile to mock EpisService, and adjust `MockEpisService` to your needs.

### Common Issues

`error="unsupported_grant_type", error_description="Unsupported grant type: mobile_id"`

Make sure you are running against the right backend environment (dev or prod).
- If you do `npm run develop` your `package.json` must proxy to `http://localhost:9000`
- If you do `npm run develop-production` your `package.json` must proxy to `https://onboarding-service.tuleva.ee`

### Known Issues

- Digital signing does not work in the dev environment. Use the production
 configuration to test it locally. See `DigiDocConfiguration.digiDocConfigDev()` and
  `smartid.hostUrl`, `smartid.relyingPartyUUID`, `smartid.relyingPartyName` config
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

### Testing ID-card Locally

In order to test ID-card locally, you need to run nginx locally with the right certificates and the right domain names.

1. Add tuleva certs to `./nginx` (4 files)
2. Update ```$frontend``` and `$backend` urls in `etc/eb/.ebextensions/nginx/conf.d/01_ssl_proxy.conf`
3. Add to `hosts` file:
   ```
   127.0.0.1 id.tuleva.ee
   127.0.0.1 pension.tuleva.ee
   127.0.0.1 onboarding-service.tuleva.ee
   ```
4. Run nginx with docker: `docker compose up nginx`
5. Add `DANGEROUSLY_DISABLE_HOST_CHECK=true` to `.env` in `onboarding-client`
6. add `server.servlet.session.cookie.domain: tuleva.ee` to `application.yml`
7. Test through https://pension.tuleva.ee
8. Later, don't forget to clean up your `hosts` file

### AWS Profile
WE use AWS SSO, to get it working properly you need to configure the profile first either by running `aws configure sso` or
pasting the following into `~/.aws/config`:
```ini
[profile tuleva]
region = eu-central-1
output = json
sso_start_url = https://tuleva.awsapps.com/start
sso_region = eu-central-1
sso_account_id = 641866833894
sso_role_name = AdministratorAccess
```

### VPN

We use AWS Client VPN. To get started, log into [AWS SSO Portal](https://tuleva.awsapps.com/start) and follow VPN Client Self Service instructions.

### Connecting to the database

- Establish VPN connection
- Configure AWS Profile and login `aws sso login`
- Connect to the DB using AWS IAM authentication where user is `iamuser` and profile `tuleva`.

### Development Environment
Configuration is available AWS S3 `s3://tulevasecrets/development-configuration/`


### RDS certificate upgrade

1. Update `.pem` file in `etc/docker`
2. If file was renamed, rename it in `gradle/packaging.gradle.kts`

In case file has multiple certificate chains, `import-certs.sh` will add all of them.

### References

[hwcrypto.js](https://github.com/hwcrypto/hwcrypto.js)

[hwcrypto Sequence Diagram](https://github.com/hwcrypto/hwcrypto.js/wiki/SequenceDiagram)

[Test Authentication Methods](https://www.id.ee/en/article/testing-the-services/)

[Test Mobile ID](https://demo.sk.ee/MIDCertsReg/)

[Test ID Card](https://demo.sk.ee/upload_cert/)

[Test Smart ID](https://github.com/SK-EID/smart-id-documentation/wiki/Smart-ID-demo)
