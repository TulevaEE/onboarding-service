package ee.tuleva.onboarding.aml;

import static ee.tuleva.onboarding.aml.AmlCheckType.*;
import static ee.tuleva.onboarding.time.ClockHolder.aYearAgo;
import static java.util.stream.Collectors.toSet;

import com.fasterxml.jackson.databind.JsonNode;
import ee.tuleva.onboarding.aml.sanctions.MatchResponse;
import ee.tuleva.onboarding.aml.sanctions.PepAndSanctionCheckService;
import ee.tuleva.onboarding.analytics.AnalyticsThirdPillar;
import ee.tuleva.onboarding.analytics.AnalyticsThirdPillarRepository;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.auth.principal.PersonImpl;
import ee.tuleva.onboarding.epis.contact.ContactDetails;
import ee.tuleva.onboarding.event.TrackableEvent;
import ee.tuleva.onboarding.event.TrackableEventType;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.address.Address;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
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
  private final AnalyticsThirdPillarRepository analyticsThirdPillarRepository;

  private final List<List<AmlCheckType>> allowedCombinations =
      List.of(
          List.of(POLITICALLY_EXPOSED_PERSON, SK_NAME, DOCUMENT, RESIDENCY_AUTO, OCCUPATION),
          List.of(POLITICALLY_EXPOSED_PERSON, SK_NAME, DOCUMENT, RESIDENCY_MANUAL, OCCUPATION),
          List.of(
              POLITICALLY_EXPOSED_PERSON,
              PENSION_REGISTRY_NAME,
              DOCUMENT,
              RESIDENCY_AUTO,
              OCCUPATION),
          List.of(
              POLITICALLY_EXPOSED_PERSON,
              PENSION_REGISTRY_NAME,
              DOCUMENT,
              RESIDENCY_MANUAL,
              OCCUPATION));

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
            .success(!hasMatch(response.results(), "sanction"))
            .metadata(metadata(response.results(), response.query()))
            .build();
    return addCheckIfMissing(sanctionCheck);
  }

  private Optional<AmlCheck> addPepCheckIfMissing(Person person, MatchResponse response) {
    AmlCheck pepCheck =
        AmlCheck.builder()
            .personalCode(person.getPersonalCode())
            .type(POLITICALLY_EXPOSED_PERSON_AUTO)
            .success(!hasMatch(response.results(), "role"))
            .metadata(metadata(response.results(), response.query()))
            .build();
    return addCheckIfMissing(pepCheck);
  }

  public void runAmlChecksOnThirdPillarCustomers() {
    List<AnalyticsThirdPillar> records =
        analyticsThirdPillarRepository.findAllByReportingDate(LocalDate.parse("2024-01-01"));
    records.forEach(
        record -> {
          MatchResponse response =
              pepAndSanctionCheckService.match(record, new Address(record.getCountry()));
          addPepCheckIfMissing(record, response);
          addSanctionCheckIfMissing(record, response);
        });
    log.info("Successfully ran checks on {} records", records.size());
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

  private Stream<JsonNode> stream(Iterable<JsonNode> jsonNodes) {
    return StreamSupport.stream(jsonNodes.spliterator(), false);
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
    return amlCheckRepository.save(amlCheck);
  }

  private boolean hasCheck(String personalCode, AmlCheckType checkType) {
    return amlCheckRepository.existsByPersonalCodeAndTypeAndCreatedTimeAfter(
        personalCode, checkType, aYearAgo());
  }

  public List<AmlCheck> getChecks(Person person) {
    return amlCheckRepository.findAllByPersonalCodeAndCreatedTimeAfter(
        person.getPersonalCode(), aYearAgo());
  }

  boolean allChecksPassed(Person person, Integer pillar) {
    if (pillar == 2) {
      // No checks needed for second pillar
      return true;
    } else if (pillar == 3) {
      val successfulTypes =
          getChecks(person).stream()
              .filter(AmlCheck::isSuccess)
              .map(AmlCheck::getType)
              .collect(toSet());
      if (allowedCombinations.stream().anyMatch(successfulTypes::containsAll)) {
        return true;
      }
    }
    log.error("All necessary AML checks not passed for person {}!", person.getPersonalCode());
    eventPublisher.publishEvent(new TrackableEvent(person, TrackableEventType.MANDATE_DENIED));

    return false;
  }
}
