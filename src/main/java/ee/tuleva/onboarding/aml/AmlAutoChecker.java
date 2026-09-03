package ee.tuleva.onboarding.aml;

import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.aml.exception.AmlChecksMissingException;
import ee.tuleva.onboarding.auth.event.AfterTokenGrantedEvent;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.country.Countries;
import ee.tuleva.onboarding.country.Country;
import ee.tuleva.onboarding.epis.ContactDetailsService;
import ee.tuleva.onboarding.epis.ContactDetailsUpdatedEvent;
import ee.tuleva.onboarding.kyc.BeforeKycCheckedEvent;
import ee.tuleva.onboarding.mandate.event.BeforeMandateCreatedEvent;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@NullMarked
public class AmlAutoChecker {

  private static final int BEFORE_USER_DETAILS_UPDATER = 1;

  private final AmlService amlService;
  private final SanctionAndPepScreener sanctionAndPepScreener;
  private final UserService userService;
  private final ContactDetailsService contactDetailsService;

  @EventListener
  @Order(BEFORE_USER_DETAILS_UPDATER)
  public void onLogin(AfterTokenGrantedEvent event) {
    Person person = event.getPerson();
    Boolean isResident = isResident(event);
    User user = getUser(person);

    amlService.checkUserBeforeLogin(user, person, isResident);
  }

  @EventListener
  public void afterLogin(AfterTokenGrantedEvent event) {
    Person person = event.getPerson();
    String accessToken = event.getAccessToken();

    userService
        .findByPersonalCode(person.getPersonalCode())
        .ifPresent(
            user -> {
              var contactDetails = contactDetailsService.getContactDetails(person, accessToken);
              amlService.addPensionRegistryNameCheckIfMissing(user, contactDetails);
            });
  }

  @EventListener
  public void contactDetailsUpdated(ContactDetailsUpdatedEvent event) {
    amlService.addContactDetailsCheckIfMissing(event.getPerson());
  }

  private User getUser(Person person) {
    return userService
        .findByPersonalCode(person.getPersonalCode())
        .orElseThrow(
            () ->
                new IllegalStateException("User not found with code " + person.getPersonalCode()));
  }

  @EventListener
  public void beforeMandateCreated(BeforeMandateCreatedEvent event) {
    User user = event.getUser();

    if (amlService.isMandateAmlCheckRequired(user, event.getMandate())) {
      Country country =
          requireNonNull(
              event.getCountry(),
              "Country missing for mandate: mandateId=" + event.getMandate().getId());
      sanctionAndPepScreener.addSanctionAndPepCheckIfMissing(user, Countries.of(country));
    }

    if (!amlService.allChecksPassed(user, event.getMandate())) {
      throw AmlChecksMissingException.newInstance();
    }
  }

  @EventListener
  public void beforeKycChecked(BeforeKycCheckedEvent event) {
    sanctionAndPepScreener.addSanctionAndPepCheckIfMissing(event.person(), event.countries());
  }

  private @Nullable Boolean isResident(AfterTokenGrantedEvent event) {
    return event.isResident();
  }
}
