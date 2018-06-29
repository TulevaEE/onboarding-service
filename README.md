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

To run Flyway migrations for the first time, uncomment these lines in `application.yml`:
```
#flyway:
#  baseline-on-migrate: true
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

### SSL
Using https://letsencrypt.org/

`ssh id.tuleva.ee`

`./certbot-auto certonly --manual`

If certs have been expired run with debug and verbose flag.
`sudo certbot certonly --debug-challenges -v --webroot -w .`

Then get acme challenges from file system from `.well-known` and update acme challenges in applications and nginx.

To generate certs for

`id.tuleva.ee,epis-service.tuleva.ee,onboarding-service.tuleva.ee,pension.tuleva.ee`

ACME challenge controller in the applications and `id.tuleva.ee` is in `nginx.conf`

Cert hosting is correspondingly in Heroku and `nginx.conf`

After generating new certificated copy them from `id.tuleva.ee` so that nginx will pick them up.

```
sudo cp -f /etc/letsencrypt/live/pension.tuleva.ee/fullchain.pem /home/ubuntu/subdomain.tuleva.ee.fullchain.pem
sudo cp -f /etc/letsencrypt/live/pension.tuleva.ee/privkey.pem /home/ubuntu/subdomain.tuleva.ee.privkey.pem
sudo service nginx restart
```

Now add the certs to Heroku, too. To all of the 3 services hosted there (epis-service, onboarding-service & onboarding-client).

### How to add new pension funds?
1. Add the new fund to the `funds` database table.
2. Add the fund name translations into the frontend `src/translations/` json files (i.e. `"target.funds.EE000000000.title": "My Pension Fund",`)

### References

[DigiDocService Documentation](http://sk-eid.github.io/dds-documentation/)

[DigiDocService Sequence Diagrams](https://eid.eesti.ee/index.php/Sample_applications#Web_form)

[MobileID library](https://github.com/ErkoRisthein/mobileid)

[hwcrypto.js](https://hwcrypto.github.io/)

[hwcrypto Sequence Diagram](https://github.com/hwcrypto/hwcrypto.js/wiki/SequenceDiagram)
