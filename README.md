# Onboarding-service

[![CircleCI](https://circleci.com/gh/TulevaEE/onboarding-service/tree/master.svg?style=shield)](https://circleci.com/gh/TulevaEE/onboarding-service/tree/master)

## Design

![N|Solid](reference/design.png)

## Tech stack

**Database:**
PostgreSQL

Running locally with docker:
```
docker run -d --name tuleva-onboarding-database \
                 -p 5432:5432 \
                 -e "POSTGRES_USER=tuleva-onboarding" \
                 -e "POSTGRES_DB=tuleva-onboarding" \
                 postgres:9.6
```

**Backend:**
Java 8, Spring Boot, Gradle, Spock for testing

**Frontend:**
React, ES6, scss, custom bootstrap, enzyme + jest for testing


**Error tracking:**
Rollbar

**Conversion funnel:**
Google Analytics / Mixpanel

**Hosting:**
Heroku

For static IP - quotaguard static Heroku plugin

**CI:**
CircleCI

### API
oAuth with mobile-ID and ID-card sign-in

[Swagger](https://onboarding-service.tuleva.ee/swagger-ui.html)

[Postman API collection](reference/api.postman_collection)

### Build pipeline

**Dev environment:**
`./gradlew bootRun`

**Production:**
Merge GitHub pull request to master -> build in CircleCI -> redeploy to Heroku (if build is green)

###Comparison maintenance
Comparison service in package ee.tuleva.onboarding.comparisons can be edited for keeping total fee calculations current with Estonian regulations.

### References

[DigiDocService Documentation](http://sk-eid.github.io/dds-documentation/)

[DigiDocService Sequence Diagrams](https://eid.eesti.ee/index.php/Sample_applications#Web_form)

[MobileID library](https://github.com/ErkoRisthein/mobileid)

[hwcrypto.js](https://hwcrypto.github.io/)

[hwcrypto Sequence Diagram](https://github.com/hwcrypto/hwcrypto.js/wiki/SequenceDiagram)
