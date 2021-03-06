package ee.tuleva.onboarding.aml;

import static ee.tuleva.onboarding.aml.AmlCheckType.*;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.util.stream.Collectors.toSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import ee.tuleva.onboarding.audit.AuditEventPublisher;
import ee.tuleva.onboarding.audit.AuditEventType;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.epis.contact.UserPreferences;
import ee.tuleva.onboarding.user.User;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AmlService {

  private final AmlCheckRepository amlCheckRepository;
  private final AuditEventPublisher auditEventPublisher;
  private final Clock clock;
  private final List<List<AmlCheckType>> allowedCombinations =
      ImmutableList.of(
          ImmutableList.of(
              POLITICALLY_EXPOSED_PERSON, SK_NAME, DOCUMENT, RESIDENCY_AUTO, OCCUPATION),
          ImmutableList.of(
              POLITICALLY_EXPOSED_PERSON, SK_NAME, DOCUMENT, RESIDENCY_MANUAL, OCCUPATION),
          ImmutableList.of(
              POLITICALLY_EXPOSED_PERSON,
              PENSION_REGISTRY_NAME,
              DOCUMENT,
              RESIDENCY_AUTO,
              OCCUPATION),
          ImmutableList.of(
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

  private void addDocumentCheck(User user) {
    AmlCheck documentCheck = AmlCheck.builder().user(user).type(DOCUMENT).success(true).build();
    addCheckIfMissing(documentCheck);
  }

  private void addResidencyCheck(User user, Boolean isResident) {
    if (isResident != null) {
      AmlCheck check =
          AmlCheck.builder().user(user).type(RESIDENCY_AUTO).success(isResident).build();
      addCheckIfMissing(check);
    }
  }

  private void addSkNameCheck(User user, Person person) {
    boolean isSuccess =
        personDataMatches(
            user, person.getFirstName(), person.getLastName(), person.getPersonalCode());
    AmlCheck skNameCheck =
        AmlCheck.builder()
            .user(user)
            .type(SK_NAME)
            .success(isSuccess)
            .metadata(metadata(user, person))
            .build();
    addCheckIfMissing(skNameCheck);
  }

  private Map<String, Object> metadata(Person user, Person person) {
    return ImmutableMap.of("user", strip(user), "person", strip(person));
  }

  private Person strip(Person person) {
    return new Person() {
      @Override
      public String getPersonalCode() {
        return person.getPersonalCode();
      }

      @Override
      public String getFirstName() {
        return person.getFirstName();
      }

      @Override
      public String getLastName() {
        return person.getLastName();
      }
    };
  }

  public void addContactDetailsCheckIfMissing(User user) {
    AmlCheck contactDetailsCheck =
        AmlCheck.builder().user(user).type(CONTACT_DETAILS).success(true).build();
    addCheckIfMissing(contactDetailsCheck);
  }

  public void addPensionRegistryNameCheckIfMissing(User user, UserPreferences userPreferences) {
    boolean isSuccess =
        personDataMatches(
            user,
            userPreferences.getFirstName(),
            userPreferences.getLastName(),
            userPreferences.getPersonalCode());
    AmlCheck pensionRegistryNameCheck =
        AmlCheck.builder()
            .user(user)
            .type(PENSION_REGISTRY_NAME)
            .success(isSuccess)
            .metadata(metadata(user, userPreferences))
            .build();
    addCheckIfMissing(pensionRegistryNameCheck);
  }

  private boolean personDataMatches(
      User user, String firstName, String lastName, String personalCode) {
    if (!StringUtils.equalsIgnoreCase(user.getFirstName(), firstName)) {
      return false;
    }
    if (!StringUtils.equalsIgnoreCase(user.getLastName(), lastName)) {
      return false;
    }
    return StringUtils.equalsIgnoreCase(user.getPersonalCode(), personalCode);
  }

  public void addCheckIfMissing(AmlCheck amlCheck) {
    if (!hasCheck(amlCheck.getUser(), amlCheck.getType())) {
      addCheck(amlCheck);
    }
  }

  private void addCheck(AmlCheck amlCheck) {
    log.info(
        "Adding check {} to user {} with success {}",
        amlCheck.getType(),
        amlCheck.getUser().getId(),
        amlCheck.isSuccess());
    amlCheckRepository.save(amlCheck);
  }

  private boolean hasCheck(User user, AmlCheckType checkType) {
    return amlCheckRepository.existsByUserAndTypeAndCreatedTimeAfter(user, checkType, aYearAgo());
  }

  public List<AmlCheck> getChecks(User user) {
    return amlCheckRepository.findAllByUserAndCreatedTimeAfter(user, aYearAgo());
  }

  boolean allChecksPassed(User user, Integer pillar) {
    if (pillar == 2) {
      // No checks needed for second pillar
      return true;
    } else if (pillar == 3) {
      val successfulTypes =
          getChecks(user).stream()
              .filter(AmlCheck::isSuccess)
              .map(AmlCheck::getType)
              .collect(toSet());
      if (allowedCombinations.stream().anyMatch(successfulTypes::containsAll)) {
        return true;
      }
    }
    log.error("All necessary AML checks not passed for user {}!", user.getId());
    auditEventPublisher.publish(user.getEmail(), AuditEventType.MANDATE_DENIED);
    return false;
  }

  private Instant aYearAgo() {
    return Instant.now(clock).minus(365, DAYS);
  }
}
