# Onboarding-service

## Design

![N|Solid](https://cldup.com/dTxpPi9lDf.thumb.png)

### API
[Swagger](https://onboarding-service.tuleva.ee/swagger-ui.html)
[Postman API collection](reference/api.postman_collection)

### Build pipeline

**Dev environment**
Gradle `bootRun`

**Production**
Github -> CircleCI (if build is green) -> deploy Heroku