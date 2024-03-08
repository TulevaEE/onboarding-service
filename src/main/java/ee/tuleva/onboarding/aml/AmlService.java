package ee.tuleva.onboarding.aml;

import static ee.tuleva.onboarding.aml.AmlCheckType.*;
import static ee.tuleva.onboarding.time.ClockHolder.aYearAgo;
import static java.util.stream.Collectors.toSet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import ee.tuleva.onboarding.aml.sanctions.MatchResponse;
import ee.tuleva.onboarding.aml.sanctions.PepAndSanctionCheckService;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.auth.principal.PersonImpl;
import ee.tuleva.onboarding.epis.contact.ContactDetails;
import ee.tuleva.onboarding.event.TrackableEvent;
import ee.tuleva.onboarding.event.TrackableEventType;
import ee.tuleva.onboarding.user.User;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

  public List<AmlCheck> addSanctionAndPepCheckIfMissing(User user, ContactDetails contactDetails) {
    MatchResponse response = pepAndSanctionCheckService.match(user, contactDetails.getCountry());
    ArrayNode results = response.results();
    JsonNode query = response.query();

    AmlCheck pepCheck = null;
    try {
      pepCheck =
          AmlCheck.builder()
              .personalCode(user.getPersonalCode())
              .type(POLITICALLY_EXPOSED_PERSON_AUTO)
              .success(!hasMatch(results, "role"))
              .metadata(metadata(results, query))
              .build();

      addCheckIfMissing(pepCheck);
    } catch (Exception e) {
      log.error("Error adding pep check", e);
    }

    AmlCheck sanctionCheck = null;
    try {
      sanctionCheck =
          AmlCheck.builder()
              .personalCode(user.getPersonalCode())
              .type(SANCTION)
              .success(!hasMatch(results, "sanction"))
              .metadata(metadata(results, query))
              .build();

      addCheckIfMissing(sanctionCheck);
    } catch (Exception e) {
      log.error("Error adding sanction check", e);
    }

    List<AmlCheck> amlChecks = new ArrayList<>();
    amlChecks.add(pepCheck);
    amlChecks.add(sanctionCheck);
    return amlChecks;
  }

  public void recheckAllPepAndSanctionChecks() {
    amlCheckRepository
        .findAllByTypeIn(List.of(SANCTION, POLITICALLY_EXPOSED_PERSON_AUTO))
        .forEach(
            amlCheck -> {
              val firstName = ""; // todo
              val lastName = ""; // todo
              val person = new PersonImpl(amlCheck.getPersonalCode(), firstName, lastName);
              MatchResponse matchResponse = pepAndSanctionCheckService.match(person, "ee");
              amlCheck.setMetadata(metadata(matchResponse.results(), matchResponse.query()));
              if (amlCheck.getType() == SANCTION) {
                amlCheck.setSuccess(!hasMatch(matchResponse.results(), "sanction"));
              } else if (amlCheck.getType() == POLITICALLY_EXPOSED_PERSON_AUTO) {
                amlCheck.setSuccess(!hasMatch(matchResponse.results(), "role"));
              }
              amlCheckRepository.save(amlCheck);

              AmlCheck pepCheck =
                  AmlCheck.builder()
                      .personalCode(amlCheck.getPersonalCode())
                      .type(POLITICALLY_EXPOSED_PERSON_AUTO)
                      .success(!hasMatch(matchResponse.results(), "role"))
                      .metadata(metadata(matchResponse.results(), matchResponse.query()))
                      .build();

              addCheckIfMissing(pepCheck);
            });
  }

  private Map<String, Object> metadata(ArrayNode results, JsonNode query) {
    return Map.of("results", results, "query", query);
  }

  private boolean hasMatch(ArrayNode results, String topicNameStartsWith) {
    return stream(results)
        .anyMatch(
            result ->
                stream((ArrayNode) result.get("properties").get("topics"))
                        .anyMatch(topic -> topic.asText().startsWith(topicNameStartsWith))
                    && result.get("match").asBoolean());
  }

  private Stream<JsonNode> stream(ArrayNode arrayNode) {
    return StreamSupport.stream(arrayNode.spliterator(), false);
  }

  private Map<String, Object> metadata(User user, Person person) {
    return Map.of("user", new PersonImpl(user), "person", new PersonImpl(person));
  }

  public void addContactDetailsCheckIfMissing(User user) {
    AmlCheck contactDetailsCheck =
        AmlCheck.builder()
            .personalCode(user.getPersonalCode())
            .type(CONTACT_DETAILS)
            .success(true)
            .build();
    addCheckIfMissing(contactDetailsCheck);
  }

  public void addPensionRegistryNameCheckIfMissing(User user, ContactDetails contactDetails) {
    boolean isSuccess = personDataMatches(user, contactDetails);
    AmlCheck pensionRegistryNameCheck =
        AmlCheck.builder()
            .personalCode(user.getPersonalCode())
            .type(PENSION_REGISTRY_NAME)
            .success(isSuccess)
            .metadata(metadata(user, contactDetails))
            .build();
    addCheckIfMissing(pensionRegistryNameCheck);
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

  public void addCheckIfMissing(AmlCheck amlCheck) {
    if (!hasCheck(amlCheck.getPersonalCode(), amlCheck.getType())) {
      addCheck(amlCheck);
    }
  }

  private void addCheck(AmlCheck amlCheck) {
    log.info(
        "Adding check {} to person {} with success {}",
        amlCheck.getType(),
        amlCheck.getPersonalCode(),
        amlCheck.isSuccess());
    amlCheckRepository.save(amlCheck);
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
