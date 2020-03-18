package ee.tuleva.onboarding.aml;

import static ee.tuleva.onboarding.aml.AmlCheckType.*;
import static java.util.stream.Collectors.toSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import ee.tuleva.onboarding.audit.AuditEventPublisher;
import ee.tuleva.onboarding.audit.AuditEventType;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.epis.contact.UserPreferences;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.user.User;
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

  public void checkUserAfterLogin(User user, Person person) {
    AmlCheck check = AmlCheck.builder().user(user).type(DOCUMENT).success(true).build();
    addCheckIfMissing(check);
    addSkNameCheckIfMissing(user, person);
  }

  private void addSkNameCheckIfMissing(User user, Person person) {
    if (!hasCheck(user, SK_NAME)) {
      boolean success =
          personDataMatches(
              user, person.getFirstName(), person.getLastName(), person.getPersonalCode());
      AmlCheck check =
          AmlCheck.builder()
              .user(user)
              .type(SK_NAME)
              .success(success)
              .metadata(metadata(user, person))
              .build();
      addCheck(check);
    }
  }

  private Map<String, Object> metadata(Person user, Person person) {
    return ImmutableMap.of("user", wrap(user), "person", wrap(person));
  }

  private Person wrap(Person person) {
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

  public void addPensionRegistryNameCheckIfMissing(User user, UserPreferences userPreferences) {
    if (!hasCheck(user, PENSION_REGISTRY_NAME)) {
      boolean success =
          personDataMatches(
              user,
              userPreferences.getFirstName(),
              userPreferences.getLastName(),
              userPreferences.getPersonalCode());
      AmlCheck check =
          AmlCheck.builder()
              .user(user)
              .type(PENSION_REGISTRY_NAME)
              .success(success)
              .metadata(metadata(user, userPreferences))
              .build();
      addCheck(check);
    }
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

  private boolean hasCheck(User user, AmlCheckType type) {
    return amlCheckRepository.existsByUserAndType(user, type);
  }

  public List<AmlCheck> getChecks(User user) {
    return amlCheckRepository.findAllByUser(user);
  }

  public boolean allChecksPassed(Mandate mandate) {
    val user = mandate.getUser();
    if (mandate.getPillar() == 2) {
      // No checks needed for second pillar
      return true;
    } else if (mandate.getPillar() == 3) {
      val checks = amlCheckRepository.findAllByUser(user);
      val successfulTypes =
          checks.stream().filter(AmlCheck::isSuccess).map(AmlCheck::getType).collect(toSet());
      if (allowedCombinations.stream().anyMatch(successfulTypes::containsAll)) {
        return true;
      }
    }
    log.error("All necessary AML checks not passed for user {}!", user.getId());
    auditEventPublisher.publish(user.getEmail(), AuditEventType.MANDATE_DENIED);
    return false;
  }
}
