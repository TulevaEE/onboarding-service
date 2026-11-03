package ee.tuleva.onboarding.epis;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.country.Country;
import ee.tuleva.onboarding.epis.ContactDetails.ContributionPeriod;
import ee.tuleva.onboarding.epis.ContactDetails.ContributionStatus;
import ee.tuleva.onboarding.user.SecondPillarContributionPeriod;
import ee.tuleva.onboarding.user.SecondPillarContributionPeriod.Status;
import ee.tuleva.onboarding.user.UserContacts;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class UserContactsAdapter implements UserContacts {

  private final ContactDetailsService contactDetailsService;

  @Override
  public ContactSummary forPerson(Person person) {
    return toContactSummary(contactDetailsService.getContactDetails(person));
  }

  @Override
  public ContactSummary forPerson(Person person, String jwtToken) {
    return toContactSummary(contactDetailsService.getContactDetails(person, jwtToken));
  }

  @Override
  public ContactSummary update(
      Person person,
      @Nullable String email,
      @Nullable String phoneNumber,
      @Nullable Country address) {
    return toContactSummary(
        contactDetailsService.updateContactDetails(person, email, phoneNumber, address));
  }

  private static ContactSummary toContactSummary(ContactDetails contactDetails) {
    return new ContactSummary(
        contactDetails.getEmail(),
        contactDetails.getPhoneNumber(),
        contactDetails.getPensionAccountNumber(),
        contactDetails.getCountry(),
        contactDetails.getActiveSecondPillarFundPik(),
        contactDetails.isSecondPillarActive(),
        contactDetails.isThirdPillarActive(),
        contactDetails.getSecondPillarOpenDate(),
        contactDetails.getThirdPillarInitDate(),
        contactDetails.getLastUpdateDate(),
        toContributionPeriods(contactDetails.getSecondPillarContributionPeriods()));
  }

  private static List<SecondPillarContributionPeriod> toContributionPeriods(
      @Nullable List<ContributionPeriod> periods) {
    if (periods == null) {
      return List.of();
    }
    return periods.stream()
        .map(
            period ->
                new SecondPillarContributionPeriod(
                    toStatus(period.status()), period.startDate(), period.endDate()))
        .toList();
  }

  private static Status toStatus(ContributionStatus status) {
    return switch (status) {
      case CONTRIBUTING -> Status.CONTRIBUTING;
      case CONTRIBUTING_WITHOUT_ACCOUNT -> Status.CONTRIBUTING_WITHOUT_ACCOUNT;
      case NOT_CONTRIBUTING -> Status.NOT_CONTRIBUTING;
      case PAUSED -> Status.PAUSED;
    };
  }
}
