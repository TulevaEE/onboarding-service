# Onboarding-service

[![CircleCI](https://circleci.com/gh/TulevaEE/onboarding-service/tree/master.svg?style=shield)](https://circleci.com/gh/TulevaEE/onboarding-service/tree/master)
[![codecov](https://codecov.io/gh/TulevaEE/onboarding-service/branch/master/graph/badge.svg)](https://codecov.io/gh/TulevaEE/onboarding-service)

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

Authentication: oAuth with Mobile-ID, ID-card and Smart-ID

[Swagger UI](https://onboarding-service.tuleva.ee/swagger-ui.html)

[Postman API collection](reference/api.postman_collection)


### Build pipeline

**Production:**
Merge GitHub pull request to master -> build in CircleCI -> auto-redeploy (if build is green)

### How to add new pension funds?
1. Add the new fund to the `funds` database table.
2. Add the fund name translations into the frontend `src/translations/` json files (i.e. `"target.funds.EE000000000.title": "My Pension Fund",`)

### Development notes

If you don't want to run epis-serivice,
then you can mock `TransferExchangeService.java`, which calls epis-service.

### Common Issues

`error="unsupported_grant_type", error_description="Unsupported grant type: mobile_id"`

Make sure you are running against the right backend environment (dev or prod).
- If you do `npm run develop` your `package.json` must proxy to `http://localhost:9000`
- If you do `npm run develop-production` your `package.json` must proxy to `https://onboarding-service.tuleva.ee`

### References

[DigiDocService Documentation](http://sk-eid.github.io/dds-documentation/)

[DigiDocService Sequence Diagrams](https://eid.eesti.ee/index.php/Sample_applications#Web_form)

[MobileID library](https://github.com/ErkoRisthein/mobileid)

[hwcrypto.js](https://hwcrypto.github.io/)

[hwcrypto Sequence Diagram](https://github.com/hwcrypto/hwcrypto.js/wiki/SequenceDiagram)

[Test Authentication Methods](https://www.id.ee/?lang=en&id=35755)

[Test Mobile ID](https://demo.sk.ee/MIDCertsReg/)

[Test ID Card](https://demo.sk.ee/upload_cert/)

[Test Smart ID](https://github.com/SK-EID/smart-id-documentation/wiki/Smart-ID-demo)
