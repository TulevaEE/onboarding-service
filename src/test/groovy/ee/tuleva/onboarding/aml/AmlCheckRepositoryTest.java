package ee.tuleva.onboarding.aml;

import static ee.tuleva.onboarding.aml.AmlCheckType.KYC_CHECK;
import static ee.tuleva.onboarding.aml.AmlCheckType.POLITICALLY_EXPOSED_PERSON_AUTO;
import static ee.tuleva.onboarding.aml.AmlCheckType.SANCTION;
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static java.time.ZoneOffset.UTC;
import static java.time.temporal.ChronoUnit.DAYS;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.user.User;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
class AmlCheckRepositoryTest {

  @Autowired private TestEntityManager entityManager;

  @Autowired private AmlCheckRepository repository;

  @AfterEach
  void tearDown() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void readsBackCompanyRiskLevelOverrideWithoutBreakingEnumDeserialization() {
    User user = sampleUser().build();
    AmlCheck override =
        AmlCheck.builder()
            .personalCode(user.getPersonalCode())
            .type(AmlCheckType.COMPANY_RISK_LEVEL_OVERRIDE)
            .success(true)
            .metadata(Map.of("level", 3, "registry_code", "12345678", "source", "manual"))
            .build();
    entityManager.persist(override);
    entityManager.flush();

    List<AmlCheck> checks =
        repository.findAllByPersonalCodeAndCreatedTimeAfter(
            user.getPersonalCode(), Instant.now().minus(365, DAYS));

    assertThat(checks)
        .extracting(AmlCheck::getType)
        .containsExactly(AmlCheckType.COMPANY_RISK_LEVEL_OVERRIDE);
  }

  @Test
  void findsTheNewestKycCheckWhenAnOlderSuccessAndANewerFailureAreBothInTheWindow() {
    String personalCode = sampleUser().build().getPersonalCode();
    persistKycCheckAt(personalCode, true, Instant.now().minus(180, DAYS));
    AmlCheck newerFailure = persistKycCheckAt(personalCode, false, Instant.now().minus(90, DAYS));

    var latest =
        repository.findFirstByPersonalCodeAndTypeAndCreatedTimeAfterOrderByCreatedTimeDescIdDesc(
            personalCode, KYC_CHECK, Instant.now().minus(365, DAYS));

    assertThat(latest).contains(newerFailure);
  }

  @Test
  void excludesKycChecksOlderThanTheCutoffWhenFindingTheNewestOne() {
    String personalCode = sampleUser().build().getPersonalCode();
    persistKycCheckAt(personalCode, true, Instant.now().minus(400, DAYS));

    var latest =
        repository.findFirstByPersonalCodeAndTypeAndCreatedTimeAfterOrderByCreatedTimeDescIdDesc(
            personalCode, KYC_CHECK, Instant.now().minus(365, DAYS));

    assertThat(latest).isEmpty();
  }

  @Test
  void breaksTiesOnIdenticalCreatedTimeByPreferringTheLaterInsertedCheck() {
    String personalCode = sampleUser().build().getPersonalCode();
    Instant sameMoment = Instant.now().minus(10, DAYS);
    persistKycCheckAt(personalCode, true, sameMoment);
    AmlCheck newerFailure = persistKycCheckAt(personalCode, false, sameMoment);

    var latest =
        repository.findFirstByPersonalCodeAndTypeAndCreatedTimeAfterOrderByCreatedTimeDescIdDesc(
            personalCode, KYC_CHECK, Instant.now().minus(365, DAYS));

    assertThat(latest).contains(newerFailure);
  }

  @Test
  void findsEachPersonWithACheckOfTheTypeAfterTheCutoffOnce() {
    persistCheckAt("38001085718", SANCTION, false, Instant.now().minus(10, DAYS));
    persistCheckAt("38001085718", SANCTION, true, Instant.now().minus(5, DAYS));
    persistCheckAt("39901019992", SANCTION, true, Instant.now().minus(400, DAYS));
    persistCheckAt("40404049996", POLITICALLY_EXPOSED_PERSON_AUTO, true, Instant.now());

    assertThat(
            repository.findPersonalCodesByTypeAndCreatedTimeAfter(
                SANCTION, Instant.now().minus(365, DAYS)))
        .containsExactly("38001085718");
  }

  private AmlCheck persistKycCheckAt(String personalCode, boolean success, Instant createdTime) {
    return persistCheckAt(personalCode, KYC_CHECK, success, createdTime);
  }

  private AmlCheck persistCheckAt(
      String personalCode, AmlCheckType type, boolean success, Instant createdTime) {
    ClockHolder.setClock(Clock.fixed(createdTime, UTC));
    AmlCheck check =
        AmlCheck.builder().personalCode(personalCode).type(type).success(success).build();
    entityManager.persist(check);
    entityManager.flush();
    ClockHolder.setDefaultClock();
    return check;
  }
}
