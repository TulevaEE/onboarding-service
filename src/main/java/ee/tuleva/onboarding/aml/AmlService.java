package ee.tuleva.onboarding.aml;

import static ee.tuleva.onboarding.aml.AmlCheckType.*;
import static ee.tuleva.onboarding.kyc.KycCheck.RiskLevel.LOW;
import static ee.tuleva.onboarding.time.ClockHolder.aYearAgo;
import static java.util.stream.Collectors.toSet;

import com.fasterxml.jackson.databind.JsonNode;
import ee.tuleva.onboarding.aml.notification.AmlCheckCreatedEvent;
import ee.tuleva.onboarding.aml.notification.AmlChecksRunEvent;
import ee.tuleva.onboarding.aml.sanctions.MatchResponse;
import ee.tuleva.onboarding.aml.sanctions.PepAndSanctionCheckService;
import ee.tuleva.onboarding.analytics.thirdpillar.AnalyticsRecentThirdPillar;
import ee.tuleva.onboarding.analytics.thirdpillar.AnalyticsRecentThirdPillarRepository;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.auth.principal.PersonImpl;
import ee.tuleva.onboarding.conversion.UserConversionService;
import ee.tuleva.onboarding.epis.contact.ContactDetails;
import ee.tuleva.onboarding.event.TrackableEvent;
import ee.tuleva.onboarding.event.TrackableEventType;
import ee.tuleva.onboarding.kyc.KycCheck;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.address.Address;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AmlService {

  private final AmlCheckRepository amlCheckRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final PepAndSanctionCheckService pepAndSanctionCheckService;
  private final AnalyticsRecentThirdPillarRepository analyticsRecentThirdPillarRepository;
  private final UserConversionService userConversionService;

  public void checkUserBeforeLogin(User user, Person person, Boolean isResident) {
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

  private void addResidencyCheck(Person person, Boolean isResident) {
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

  public List<AmlCheck> addSanctionAndPepCheckIfMissing(Person person, Address address) {
    MatchResponse response;
    try {
      response = pepAndSanctionCheckService.match(person, address);
    } catch (RuntimeException e) {
      log.error("Error calling matching service", e);
      return List.of();
    }

    Optional<AmlCheck> pepCheck = addPepCheckIfMissing(person, response);
    Optional<AmlCheck> sanctionCheck = addSanctionCheckIfMissing(person, response);

    return Stream.of(pepCheck, sanctionCheck).flatMap(Optional::stream).toList();
  }

  private Optional<AmlCheck> addSanctionCheckIfMissing(Person person, MatchResponse response) {
    AmlCheck sanctionCheck =
        AmlCheck.builder()
            .personalCode(person.getPersonalCode())
            .type(SANCTION)
            .success(isSuccess(person, SANCTION_OVERRIDE, response, "sanction"))
            .metadata(metadata(response.results(), response.query()))
            .build();
    return addCheckIfMissing(sanctionCheck);
  }

  private Optional<AmlCheck> addPepCheckIfMissing(Person person, MatchResponse response) {
    AmlCheck pepCheck =
        AmlCheck.builder()
            .personalCode(person.getPersonalCode())
            .type(POLITICALLY_EXPOSED_PERSON_AUTO)
            .success(isSuccess(person, POLITICALLY_EXPOSED_PERSON_OVERRIDE, response, "role"))
            .metadata(metadata(response.results(), response.query()))
            .build();
    return addCheckIfMissing(pepCheck);
  }

  private boolean isSuccess(
      Person person, AmlCheckType overrideType, MatchResponse response, String topic) {
    boolean hasMatch = hasMatch(response.results(), topic);
    boolean hasSuccessOverride = hasSuccessOverride(person, overrideType, response.results());
    return !hasMatch || hasSuccessOverride;
  }

  private boolean hasSuccessOverride(
      Person person, AmlCheckType overrideType, Iterable<JsonNode> results) {
    List<String> ids = stream(results).map(result -> result.path("id").asText()).toList();
    List<AmlCheck> successOverrides =
        amlCheckRepository.findAllByPersonalCodeAndTypeAndSuccess(
            person.getPersonalCode(), overrideType, true);
    return successOverrides.stream()
        .anyMatch(
            check -> {
              @SuppressWarnings("unchecked")
              var foundResults = (Iterable<Map<String, String>>) check.getMetadata().get("results");
              return stream(foundResults).anyMatch(result -> ids.contains(result.get("id")));
            });
  }

  public void runAmlChecksOnThirdPillarCustomers() {
    List<AnalyticsRecentThirdPillar> records = analyticsRecentThirdPillarRepository.findAll();

    log.info("Running III pillar AML checks on {} records", records.size());
    eventPublisher.publishEvent(new AmlChecksRunEvent(records.size()));

    records.forEach(
        record -> {
          MatchResponse response =
              pepAndSanctionCheckService.match(record, new Address(record.getCountry()));
          addPepCheckIfMissing(record, response);
          addSanctionCheckIfMissing(record, response);
        });

    log.info("Successfully ran III pillar AML checks on {} records", records.size());
  }

  private Map<String, Object> metadata(JsonNode results, JsonNode query) {
    return Map.of("results", results, "query", query);
  }

  private boolean hasMatch(Iterable<JsonNode> results, String topicNameStartsWith) {
    return stream(results)
        .anyMatch(
            result ->
                stream(result.get("properties").get("topics"))
                        .anyMatch(topic -> topic.asText().startsWith(topicNameStartsWith))
                    && result.get("match").asBoolean());
  }

  private <T> Stream<T> stream(Iterable<T> iterable) {
    return StreamSupport.stream(iterable.spliterator(), false);
  }

  private Map<String, Object> metadata(User user, Person person) {
    return Map.of("user", new PersonImpl(user), "person", new PersonImpl(person));
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
            .success(kycCheck.riskLevel() == LOW)
            .metadata(Map.of("score", kycCheck.score(), "riskLevel", kycCheck.riskLevel().name()))
            .build();
    return addCheckIfMissing(check);
  }

  private boolean personDataMatches(Person person1, Person person2) {
    if (!StringUtils.equalsIgnoreCase(person1.getFirstName(), person2.getFirstName())) {
      return false;
    }
    if (!StringUtils.equalsIgnoreCase(person1.getLastName(), person2.getLastName())) {
      return false;
    }
    return StringUtils.equalsIgnoreCase(person1.getPersonalCode(), person2.getPersonalCode());
  }

  public Optional<AmlCheck> addCheckIfMissing(AmlCheck amlCheck) {
    if (hasCheck(amlCheck.getPersonalCode(), amlCheck.getType())) {
      return Optional.empty();
    }
    AmlCheck check = addCheck(amlCheck);
    return Optional.of(check);
  }

  private AmlCheck addCheck(AmlCheck amlCheck) {
    log.info(
        "Adding check {} to person {} with success {}",
        amlCheck.getType(),
        amlCheck.getPersonalCode(),
        amlCheck.isSuccess());
    AmlCheck saved = amlCheckRepository.save(amlCheck);
    eventPublisher.publishEvent(new AmlCheckCreatedEvent(saved));
    return saved;
  }

  private boolean hasCheck(String personalCode, AmlCheckType checkType) {
    return amlCheckRepository.existsByPersonalCodeAndTypeAndCreatedTimeAfter(
        personalCode, checkType, aYearAgo());
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

    var pepCheck =
        successfulTypes.contains(POLITICALLY_EXPOSED_PERSON)
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
    log.error("All necessary AML checks not passed for user userId={}", user.getId());
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

    // intentionally returning boolean literal and not expression for clarity
    if (!isTulevaThirdPillarClient && isWithdrawalMandate) {
      return false;
    }

    return true;
  }
}
