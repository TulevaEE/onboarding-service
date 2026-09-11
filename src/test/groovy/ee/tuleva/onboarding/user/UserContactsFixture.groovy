package ee.tuleva.onboarding.user

import ee.tuleva.onboarding.user.UserContacts.ContactSummary

import java.time.Instant

class UserContactsFixture {

  static ContactSummary contactSummaryFixture(String country = "EE") {
    new ContactSummary(
        "tuleva@tuleva.ee",
        "+372546545",
        "993432432",
        country,
        null,
        true,
        true,
        Instant.parse("2019-10-01T12:13:27.141Z"),
        Instant.parse("2019-10-01T12:13:27.141Z"),
        Instant.parse("2019-10-01T12:13:27.141Z"))
  }
}
