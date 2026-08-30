package ee.tuleva.onboarding.aml;

import static ee.tuleva.onboarding.aml.AmlCheckType.*;
import static ee.tuleva.onboarding.kyc.KycCheck.RiskLevel.LOW;
import static ee.tuleva.onboarding.kyc.KycCheck.RiskLevel.NONE;
import static ee.tuleva.onboarding.time.ClockHolder.aYearAgo;
import static java.util.stream.Collectors.toSet;

import ee.tuleva.onboarding.aml.notification.AmlCheckCreatedEvent;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.auth.principal.PersonImpl;
import ee.tuleva.onboarding.conversion.UserConversionService;
import ee.tuleva.onboarding.epis.ContactDetails;
import ee.tuleva.onboarding.event.TrackableEvent;
import ee.tuleva.onboarding.event.TrackableEventType;
import ee.tuleva.onboarding.kyc.KycCheck;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.user.User;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Strings;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AmlService {

  private final AmlCheckRepository amlCheckRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final UserConversionService userConversionService;

  public void checkUserBeforeLogin(User user, Person person, @Nullable Boolean isResident) {
    addDocumentCheck(user);
    addResidencyCheck(user, isResident);
    addSkNameCheck(user, person);
  }

  private void addDocumentCheck(Person person) {
    AmlCheck documentCheck =
        AmlCheck.builder()
            .personalCode(person.getPersonalCode())
            .type(DOCUMENT)
            .success(true)
            .build();
    addCheckIfMissing(documentCheck);
  }

  private void addResidencyCheck(Person person, @Nullable Boolean isResident) {
    if (isResident != null) {
      AmlCheck check =
          AmlCheck.builder()
              .personalCode(person.getPersonalCode())
              .type(RESIDENCY_AUTO)
              .success(isResident)
              .build();
      addCheckIfMissing(check);
    }
  }

  private void addSkNameCheck(User user, Person person) {
    boolean isSuccess = personDataMatches(user, person);
    AmlCheck skNameCheck =
        AmlCheck.builder()
            .personalCode(user.getPersonalCode())
            .type(SK_NAME)
            .success(isSuccess)
            .metadata(metadata(user, person))
            .build();
    addCheckIfMissing(skNameCheck);
  }

  public Optional<AmlCheck> addContactDetailsCheckIfMissing(Person user) {
    AmlCheck contactDetailsCheck =
        AmlCheck.builder()
            .personalCode(user.getPersonalCode())
            .type(CONTACT_DETAILS)
            .success(true)
            .build();
    return addCheckIfMissing(contactDetailsCheck);
  }

  public Optional<AmlCheck> addPensionRegistryNameCheckIfMissing(
      User user, ContactDetails contactDetails) {
    boolean isSuccess = personDataMatches(user, contactDetails);
    AmlCheck pensionRegistryNameCheck =
        AmlCheck.builder()
            .personalCode(user.getPersonalCode())
            .type(PENSION_REGISTRY_NAME)
            .success(isSuccess)
            .metadata(metadata(user, contactDetails))
            .build();
    return addCheckIfMissing(pensionRegistryNameCheck);
  }

  public Optional<AmlCheck> addKycCheck(String personalCode, KycCheck kycCheck) {
    AmlCheck check =
        AmlCheck.builder()
            .personalCode(personalCode)
            .type(KYC_CHECK)
            .success(kycCheck.riskLevel() == LOW || kycCheck.riskLevel() == NONE)
            .metadata(kycCheck.metadata())
            .build();
    if (check.isSuccess() && latestCheckIsSuccessful(personalCode, KYC_CHECK)) {
      return Optional.empty();
    }
    return Optional.of(addCheck(check));
  }

  public AmlCheck addCustodyRightCheck(
      String personalCode, boolean success, Map<String, Object> metadata) {
    return addCheck(
        AmlCheck.builder()
            .personalCode(personalCode)
            .type(CUSTODY_RIGHT)
            .success(success)
            .metadata(metadata)
            .build());
  }

  private boolean personDataMatches(Person person1, Person person2) {
    if (!Strings.CI.equals(person1.getFirstName(), person2.getFirstName())) {
      return false;
    }
    if (!Strings.CI.equals(person1.getLastName(), person2.getLastName())) {
      return false;
    }
    return Strings.CI.equals(person1.getPersonalCode(), person2.getPersonalCode());
  }

  private Map<String, Object> metadata(User user, Person person) {
    return Map.of("user", new PersonImpl(user), "person", new PersonImpl(person));
  }

  public Optional<AmlCheck> addCheckIfMissing(AmlCheck amlCheck) {
    if (hasCheck(amlCheck.getPersonalCode(), amlCheck.getType())) {
      return Optional.empty();
    }
    AmlCheck check = addCheck(amlCheck);
    return Optional.of(check);
  }

  public AmlCheck addCheck(AmlCheck amlCheck) {
    log.info(
        "Adding check {} to person {} with success {}",
        amlCheck.getType(),
        amlCheck.getPersonalCode(),
        amlCheck.isSuccess());
    AmlCheck saved = amlCheckRepository.save(amlCheck);
    eventPublisher.publishEvent(new AmlCheckCreatedEvent(this, saved));
    return saved;
  }

  boolean hasCheck(String personalCode, AmlCheckType checkType) {
    return amlCheckRepository.existsByPersonalCodeAndTypeAndCreatedTimeAfter(
        personalCode, checkType, aYearAgo());
  }

  private boolean latestCheckIsSuccessful(String personalCode, AmlCheckType checkType) {
    return amlCheckRepository
        .findFirstByPersonalCodeAndTypeAndCreatedTimeAfterOrderByCreatedTimeDescIdDesc(
            personalCode, checkType, aYearAgo())
        .filter(AmlCheck::isSuccess)
        .isPresent();
  }

  public List<AmlCheck> getChecks(Person person) {
    return amlCheckRepository.findAllByPersonalCodeAndCreatedTimeAfter(
        person.getPersonalCode(), aYearAgo());
  }

  boolean allChecksPassed(User user, Mandate mandate) {
    if (!isMandateAmlCheckRequired(user, mandate)) {
      return true;
    }
    var successfulTypes =
        getChecks(user).stream()
            .filter(AmlCheck::isSuccess)
            .map(AmlCheck::getType)
            .collect(toSet());

    var allTypes = getChecks(user).stream().map(AmlCheck::getType).collect(toSet());
    var hasManualPepDeclaration = allTypes.contains(POLITICALLY_EXPOSED_PERSON);
    var pepCheck =
        hasManualPepDeclaration
            || successfulTypes.contains(POLITICALLY_EXPOSED_PERSON_AUTO)
            || successfulTypes.contains(POLITICALLY_EXPOSED_PERSON_OVERRIDE);
    var sanctionCheck =
        successfulTypes.contains(SANCTION) || successfulTypes.contains(SANCTION_OVERRIDE);
    var nameCheck =
        successfulTypes.contains(SK_NAME) || successfulTypes.contains(PENSION_REGISTRY_NAME);
    var documentCheck = successfulTypes.contains(DOCUMENT);
    var occupationCheck = successfulTypes.contains(OCCUPATION);
    var residencyCheck =
        successfulTypes.contains(RESIDENCY_AUTO) || successfulTypes.contains(RESIDENCY_MANUAL);

    if (pepCheck
        && sanctionCheck
        && nameCheck
        && documentCheck
        && occupationCheck
        && residencyCheck) {
      return true;
    }
    log.info("All necessary AML checks not passed for user userId={}", user.getId());
    eventPublisher.publishEvent(new TrackableEvent(user, TrackableEventType.MANDATE_DENIED));

    return false;
  }

  boolean isMandateAmlCheckRequired(User user, Mandate mandate) {
    if (mandate.getPillar() == 2) {
      return false;
    }

    var conversion = userConversionService.getConversion(user).getThirdPillar();
    var isTulevaThirdPillarClient =
        conversion.isPartiallyConverted() || conversion.isFullyConverted();
    var isWithdrawalMandate = mandate.getMandateType().isWithdrawalType();

    if (!isTulevaThirdPillarClient && isWithdrawalMandate) {
      return false;
    }

    return true;
  }
}
