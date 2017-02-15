# Onboarding-service

## Design

![N|Solid](reference/design.png)

## Tech stack

**Database**
PostgreSQL

**Backend**
Java 8, Spring Boot, Gradle, Spock for testing

**Frontend**
React, ES6, scss, custom bootstrap, enzyme + jest for testing


**Error tracking**
Rollbar

**Conversion funnel**
Google Analytics / Mixpanel


**Hosting**
Heroku

**CI**
CircleCI

### API
oAuth with mobile-id and id-card sign-in

[Swagger](https://onboarding-service.tuleva.ee/swagger-ui.html)

[Postman API collection](reference/api.postman_collection)

### Build pipeline

**Dev environment**
Gradle `bootRun`

**Production**
Github -> CircleCI (if build is green) -> deploy Heroku

###Comparison maintenance
Comparison service in package ee.tuleva.onboarding.comparisons can be edited for keeping total fee calculations current with Estonian regulations.
