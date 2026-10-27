package ee.tuleva.onboarding.user

import ee.tuleva.onboarding.user.SecondPillarContributionPeriod.Status
import ee.tuleva.onboarding.user.UserContacts.ContactSummary

import java.time.Instant
import java.time.LocalDate

class UserContactsFixture {

  static ContactSummary contactSummaryFixture(String country = "EE") {
    contactSummary(country, [])
  }

  static ContactSummary contactSummaryWithContributionPeriods(
      List<SecondPillarContributionPeriod> contributionPeriods) {
    contactSummary("EE", contributionPeriods)
  }

  static SecondPillarContributionPeriod contributionPeriod(Status status, String startDate) {
    new SecondPillarContributionPeriod(status, LocalDate.parse(startDate), null)
  }

  static SecondPillarContributionPeriod contributionPeriod(
      Status status, String startDate, String endDate) {
    new SecondPillarContributionPeriod(status, LocalDate.parse(startDate), LocalDate.parse(endDate))
  }

  private static ContactSummary contactSummary(
      String country, List<SecondPillarContributionPeriod> contributionPeriods) {
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
        Instant.parse("2019-10-01T12:13:27.141Z"),
        contributionPeriods)
  }
}
