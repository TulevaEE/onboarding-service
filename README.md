# Onboarding-service

## Design

![N|Solid](reference/design.png)

### API
[Swagger](https://onboarding-service.tuleva.ee/swagger-ui.html)

[Postman API collection](reference/api.postman_collection)

### Build pipeline

**Dev environment**
Gradle `bootRun`

**Production**
Github -> CircleCI (if build is green) -> deploy Heroku