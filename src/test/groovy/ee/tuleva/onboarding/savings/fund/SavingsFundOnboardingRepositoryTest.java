package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.aml.AmlCheckType.*;
import static ee.tuleva.onboarding.party.PartyId.Type.LEGAL_ENTITY;
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.aml.AmlCheck;
import ee.tuleva.onboarding.aml.AmlCheckType;
import ee.tuleva.onboarding.company.Company;
import ee.tuleva.onboarding.time.ClockHolder;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(SavingsFundOnboardingRepository.class)
class SavingsFundOnboardingRepositoryTest {

  @Autowired SavingsFundOnboardingRepository repository;
  @Autowired TestEntityManager entityManager;

  @AfterEach
  void resetClock() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void findsCompletedLegalEntitiesWithFailedOwnershipChecksSince() {
    var since = Instant.parse("2026-07-02T00:00:00Z");

    var flagged = company("11111111");
    repository.saveOnboardingStatus("11111111", LEGAL_ENTITY, COMPLETED);
    amlCheck(flagged, KYB_SOLE_MEMBER_OWNERSHIP, false, since.plusSeconds(60));

    var passing = company("22222222");
    repository.saveOnboardingStatus("22222222", LEGAL_ENTITY, COMPLETED);
    amlCheck(passing, KYB_DUAL_MEMBER_OWNERSHIP, true, since.plusSeconds(60));

    var alreadyRejected = company("33333333");
    repository.saveOnboardingStatus("33333333", LEGAL_ENTITY, REJECTED);
    amlCheck(alreadyRejected, KYB_SOLE_MEMBER_OWNERSHIP, false, since.plusSeconds(60));

    var staleFailure = company("44444444");
    repository.saveOnboardingStatus("44444444", LEGAL_ENTITY, COMPLETED);
    amlCheck(staleFailure, KYB_SINGLE_BOARD_MEMBER_OWNERSHIP, false, since.minusSeconds(3600));

    var nonOwnershipFailure = company("55555555");
    repository.saveOnboardingStatus("55555555", LEGAL_ENTITY, COMPLETED);
    amlCheck(nonOwnershipFailure, KYB_COMPANY_ACTIVE, false, since.plusSeconds(60));

    var result = repository.findCompletedLegalEntitiesWithFailedOwnershipChecksSince(since);

    assertThat(result).containsExactly("11111111");
  }

  private Company company(String registryCode) {
    return entityManager.persist(
        Company.builder().registryCode(registryCode).name("Test OÜ " + registryCode).build());
  }

  private void amlCheck(Company company, AmlCheckType type, boolean success, Instant createdTime) {
    ClockHolder.setClock(Clock.fixed(createdTime, ZoneOffset.UTC));
    entityManager.persist(
        AmlCheck.builder()
            .personalCode("38501010002")
            .companyId(company.getId())
            .type(type)
            .success(success)
            .build());
    entityManager.flush();
  }

  @Test
  void isOnboardingCompleted_returnsTrueOnlyWhenStatusIsCompleted() {
    repository.saveOnboardingStatus("38009293505", PERSON, COMPLETED);
    repository.saveOnboardingStatus("39609307495", PERSON, PENDING);
    repository.saveOnboardingStatus("39910273027", PERSON, REJECTED);

    assertThat(repository.isOnboardingCompleted("38009293505", PERSON)).isTrue();
    assertThat(repository.isOnboardingCompleted("39609307495", PERSON)).isFalse();
    assertThat(repository.isOnboardingCompleted("39910273027", PERSON)).isFalse();
    assertThat(repository.isOnboardingCompleted("37603135585", PERSON)).isFalse();
  }

  @Test
  void saveOnboardingStatus_insertsNewRecord() {
    repository.saveOnboardingStatus("37605030299", PERSON, PENDING);

    assertThat(repository.findStatus("37605030299", PERSON)).contains(PENDING);
  }

  @Test
  void saveOnboardingStatus_updatesExistingRecord() {
    repository.saveOnboardingStatus("39201070898", PERSON, PENDING);
    repository.saveOnboardingStatus("39201070898", PERSON, COMPLETED);

    assertThat(repository.findStatus("39201070898", PERSON)).contains(COMPLETED);
  }

  @Test
  void saveOnboardingStatus_separatesPersonAndLegalEntity() {
    repository.saveOnboardingStatus("14118923", LEGAL_ENTITY, COMPLETED);

    assertThat(repository.isOnboardingCompleted("14118923", LEGAL_ENTITY)).isTrue();
    assertThat(repository.isOnboardingCompleted("14118923", PERSON)).isFalse();
  }

  @Test
  void insertOnboardingStatusIfAbsent_insertsNewRecord() {
    repository.insertOnboardingStatusIfAbsent("60001019906", PERSON, PENDING);

    assertThat(repository.findStatus("60001019906", PERSON)).contains(PENDING);
  }

  @Test
  void insertOnboardingStatusIfAbsent_doesNotOverwriteExistingRecord() {
    repository.saveOnboardingStatus("60001019906", PERSON, COMPLETED);

    repository.insertOnboardingStatusIfAbsent("60001019906", PERSON, PENDING);

    assertThat(repository.findStatus("60001019906", PERSON)).contains(COMPLETED);
  }
}
