package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.aml.AmlCheckType.*;
import static ee.tuleva.onboarding.kyb.KybCheckType.DUAL_MEMBER_OWNERSHIP;
import static ee.tuleva.onboarding.kyb.KybCheckType.HIGH_RISK_NACE;
import static ee.tuleva.onboarding.kyb.KybCheckType.SINGLE_BOARD_MEMBER_OWNERSHIP;
import static ee.tuleva.onboarding.kyb.KybCheckType.SOLE_MEMBER_OWNERSHIP;
import static ee.tuleva.onboarding.party.PartyId.Type.LEGAL_ENTITY;
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus.*;
import static ee.tuleva.onboarding.savings.fund.OnboardingStatusChangeReason.AML_SERVICE_REVIEW;
import static ee.tuleva.onboarding.savings.fund.OnboardingStatusChangeReason.CHILD_ONBOARDING_STARTED;
import static ee.tuleva.onboarding.savings.fund.OnboardingStatusChangeReason.KYB_CHECK;
import static ee.tuleva.onboarding.savings.fund.OnboardingStatusChangeReason.KYC_CHECK;
import static ee.tuleva.onboarding.savings.fund.OnboardingStatusChangeReason.ONBOARDING_RULE_RERUN;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.aml.AmlCheck;
import ee.tuleva.onboarding.aml.AmlCheckType;
import ee.tuleva.onboarding.company.Company;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.fund.OnboardingStatusChanges.Change;
import ee.tuleva.onboarding.time.ClockConfig;
import ee.tuleva.onboarding.time.ClockHolder;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@DataJpaTest
@Import({SavingsFundOnboardingRepository.class, ClockConfig.class})
class SavingsFundOnboardingRepositoryTest {

  @Autowired SavingsFundOnboardingRepository repository;
  @Autowired TestEntityManager entityManager;
  @Autowired JdbcClient jdbcClient;

  OnboardingStatusChanges statusChanges;

  @BeforeEach
  void readStatusChanges() {
    statusChanges = new OnboardingStatusChanges(jdbcClient);
  }

  @AfterEach
  void resetClock() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void findsThePersonsOfAStatusLastChangedBeforeAnInstant() {
    var cutoff = Instant.parse("2026-11-16T22:00:00Z");
    ClockHolder.setClock(Clock.fixed(cutoff.minusSeconds(1), ZoneOffset.UTC));
    repository.saveOnboardingStatus("38888888881", PERSON, REJECTED, KYC_CHECK);
    repository.saveOnboardingStatus("38888888892", PERSON, PENDING, KYC_CHECK);
    repository.saveOnboardingStatus("12345678", LEGAL_ENTITY, REJECTED, KYB_CHECK);
    ClockHolder.setClock(Clock.fixed(cutoff, ZoneOffset.UTC));
    repository.saveOnboardingStatus("38888888903", PERSON, REJECTED, KYC_CHECK);

    assertThat(repository.findPersonCodes(REJECTED, cutoff)).containsExactly("38888888881");
    assertThat(repository.findPersonCodes(PENDING, cutoff.plusSeconds(1)))
        .containsExactly("38888888892");
  }

  @Test
  void changesAStatusOnlyFromTheOneExpected() {
    repository.saveOnboardingStatus("12345678", LEGAL_ENTITY, ON_HOLD, KYB_CHECK);

    assertThat(
            repository.changeStatus(
                "12345678", LEGAL_ENTITY, PENDING, COMPLETED, AML_SERVICE_REVIEW))
        .isFalse();
    assertThat(
            repository.changeStatus(
                "12345678", LEGAL_ENTITY, ON_HOLD, COMPLETED, AML_SERVICE_REVIEW))
        .isTrue();
    assertThat(repository.findStatus("12345678", LEGAL_ENTITY)).contains(COMPLETED);
  }

  @Test
  void holdsOnlyAPersonsRejectionMadeBeforeTheCutoff() {
    var cutoff = Instant.parse("2026-11-16T22:00:00Z");
    ClockHolder.setClock(Clock.fixed(cutoff.minusSeconds(1), ZoneOffset.UTC));
    repository.saveOnboardingStatus("38888888881", PERSON, REJECTED, KYC_CHECK);
    repository.saveOnboardingStatus("38888888892", PERSON, COMPLETED, KYC_CHECK);
    repository.saveOnboardingStatus("12345678", LEGAL_ENTITY, REJECTED, KYB_CHECK);
    ClockHolder.setClock(Clock.fixed(cutoff, ZoneOffset.UTC));
    repository.saveOnboardingStatus("38888888903", PERSON, REJECTED, KYC_CHECK);
    ClockHolder.setClock(Clock.fixed(cutoff.plusSeconds(3600), ZoneOffset.UTC));

    assertThat(repository.holdRejected("38888888881", PERSON, cutoff, ONBOARDING_RULE_RERUN))
        .isTrue();
    assertThat(repository.holdRejected("38888888881", PERSON, cutoff, ONBOARDING_RULE_RERUN))
        .isFalse();
    assertThat(repository.holdRejected("38888888892", PERSON, cutoff, ONBOARDING_RULE_RERUN))
        .isFalse();
    assertThat(repository.holdRejected("12345678", PERSON, cutoff, ONBOARDING_RULE_RERUN))
        .isFalse();
    assertThat(repository.holdRejected("38888888903", PERSON, cutoff, ONBOARDING_RULE_RERUN))
        .isFalse();

    assertThat(repository.findStatus("38888888881", PERSON)).contains(ON_HOLD);
    assertThat(repository.findStatus("38888888892", PERSON)).contains(COMPLETED);
    assertThat(repository.findStatus("12345678", LEGAL_ENTITY)).contains(REJECTED);
    assertThat(repository.findStatus("38888888903", PERSON)).contains(REJECTED);
  }

  @Test
  void findsCompletedLegalEntitiesWithFailedOwnershipChecksSince() {
    var since = Instant.parse("2026-07-02T00:00:00Z");

    var flagged = company("11111111");
    repository.saveOnboardingStatus("11111111", LEGAL_ENTITY, COMPLETED, KYB_CHECK);
    amlCheck(flagged, KYB_SOLE_MEMBER_OWNERSHIP, false, since.plusSeconds(60));

    var passing = company("22222222");
    repository.saveOnboardingStatus("22222222", LEGAL_ENTITY, COMPLETED, KYB_CHECK);
    amlCheck(passing, KYB_DUAL_MEMBER_OWNERSHIP, true, since.plusSeconds(60));

    var alreadyRejected = company("33333333");
    repository.saveOnboardingStatus("33333333", LEGAL_ENTITY, REJECTED, KYB_CHECK);
    amlCheck(alreadyRejected, KYB_SOLE_MEMBER_OWNERSHIP, false, since.plusSeconds(60));

    var staleFailure = company("44444444");
    repository.saveOnboardingStatus("44444444", LEGAL_ENTITY, COMPLETED, KYB_CHECK);
    amlCheck(staleFailure, KYB_SINGLE_BOARD_MEMBER_OWNERSHIP, false, since.minusSeconds(3600));

    var nonOwnershipFailure = company("55555555");
    repository.saveOnboardingStatus("55555555", LEGAL_ENTITY, COMPLETED, KYB_CHECK);
    amlCheck(nonOwnershipFailure, KYB_COMPANY_ACTIVE, false, since.plusSeconds(60));

    var result =
        repository.findCompletedLegalEntitiesWithFailedChecksSince(
            since,
            List.of(SOLE_MEMBER_OWNERSHIP, DUAL_MEMBER_OWNERSHIP, SINGLE_BOARD_MEMBER_OWNERSHIP));

    assertThat(result).containsExactly("11111111");
  }

  @Test
  void findsCompletedLegalEntitiesWithAFailedActivityCheckSince() {
    var since = Instant.parse("2026-07-02T00:00:00Z");

    var highRiskActivity = company("11111111");
    repository.saveOnboardingStatus("11111111", LEGAL_ENTITY, COMPLETED, KYB_CHECK);
    amlCheck(highRiskActivity, KYB_HIGH_RISK_NACE, false, since.plusSeconds(60));

    var reviewedAndOverridden = company("22222222");
    repository.saveOnboardingStatus("22222222", LEGAL_ENTITY, COMPLETED, KYB_CHECK);
    amlCheck(reviewedAndOverridden, KYB_HIGH_RISK_NACE, true, since.plusSeconds(60));

    var stillApplying = company("33333333");
    repository.saveOnboardingStatus("33333333", LEGAL_ENTITY, PENDING, KYB_CHECK);
    amlCheck(stillApplying, KYB_HIGH_RISK_NACE, false, since.plusSeconds(60));

    var ownershipFailure = company("44444444");
    repository.saveOnboardingStatus("44444444", LEGAL_ENTITY, COMPLETED, KYB_CHECK);
    amlCheck(ownershipFailure, KYB_DUAL_MEMBER_OWNERSHIP, false, since.plusSeconds(60));

    var result =
        repository.findCompletedLegalEntitiesWithFailedChecksSince(since, List.of(HIGH_RISK_NACE));

    assertThat(result).containsExactly("11111111");
  }

  @Test
  void recordsEveryStatusChangeWithTheStatusBeforeItTheReasonAndTheTime() {
    var startedAt = Instant.parse("2026-11-18T10:00:00Z");
    var heldAt = startedAt.plusSeconds(60);
    var reviewedAt = startedAt.plusSeconds(3600);
    ClockHolder.setClock(Clock.fixed(startedAt, ZoneOffset.UTC));
    repository.saveOnboardingStatus("38888888881", PERSON, PENDING, KYC_CHECK);
    ClockHolder.setClock(Clock.fixed(heldAt, ZoneOffset.UTC));
    repository.saveOnboardingStatus("38888888881", PERSON, ON_HOLD, KYC_CHECK);
    ClockHolder.setClock(Clock.fixed(reviewedAt, ZoneOffset.UTC));
    repository.saveOnboardingStatus("38888888881", PERSON, COMPLETED, AML_SERVICE_REVIEW);

    assertThat(statusChanges.of("38888888881", PERSON))
        .containsExactly(
            new Change(null, PENDING, KYC_CHECK, startedAt),
            new Change(PENDING, ON_HOLD, KYC_CHECK, heldAt),
            new Change(ON_HOLD, COMPLETED, AML_SERVICE_REVIEW, reviewedAt));
  }

  @Test
  void recordsNoChangeWhenTheSameStatusIsSavedAgain() {
    var completedAt = Instant.parse("2026-11-18T10:00:00Z");
    ClockHolder.setClock(Clock.fixed(completedAt, ZoneOffset.UTC));
    repository.saveOnboardingStatus("12345678", LEGAL_ENTITY, COMPLETED, KYB_CHECK);
    ClockHolder.setClock(Clock.fixed(completedAt.plusSeconds(60), ZoneOffset.UTC));
    repository.saveOnboardingStatus("12345678", LEGAL_ENTITY, COMPLETED, KYB_CHECK);

    assertThat(statusChanges.of("12345678", LEGAL_ENTITY))
        .containsExactly(new Change(null, COMPLETED, KYB_CHECK, completedAt));
  }

  @Test
  void recordsAChangeFromTheExpectedStatusOnlyWhenItIsMade() {
    var heldAt = Instant.parse("2026-11-18T10:00:00Z");
    var reviewedAt = heldAt.plusSeconds(3600);
    ClockHolder.setClock(Clock.fixed(heldAt, ZoneOffset.UTC));
    repository.saveOnboardingStatus("12345678", LEGAL_ENTITY, ON_HOLD, KYB_CHECK);
    ClockHolder.setClock(Clock.fixed(reviewedAt, ZoneOffset.UTC));

    repository.changeStatus("12345678", LEGAL_ENTITY, PENDING, REJECTED, AML_SERVICE_REVIEW);
    repository.changeStatus("12345678", LEGAL_ENTITY, ON_HOLD, COMPLETED, AML_SERVICE_REVIEW);

    assertThat(statusChanges.of("12345678", LEGAL_ENTITY))
        .containsExactly(
            new Change(null, ON_HOLD, KYB_CHECK, heldAt),
            new Change(ON_HOLD, COMPLETED, AML_SERVICE_REVIEW, reviewedAt));
  }

  @Test
  void recordsARejectionHeldForReviewAgainOnlyWhenItIsHeld() {
    var rejectedAt = Instant.parse("2026-11-16T10:00:00Z");
    var cutoff = Instant.parse("2026-11-16T22:00:00Z");
    var heldAt = Instant.parse("2026-11-18T18:00:00Z");
    ClockHolder.setClock(Clock.fixed(rejectedAt, ZoneOffset.UTC));
    repository.saveOnboardingStatus("38888888881", PERSON, REJECTED, KYC_CHECK);
    ClockHolder.setClock(Clock.fixed(heldAt, ZoneOffset.UTC));

    repository.holdRejected("38888888881", PERSON, cutoff, ONBOARDING_RULE_RERUN);
    repository.holdRejected("38888888881", PERSON, cutoff, ONBOARDING_RULE_RERUN);

    assertThat(statusChanges.of("38888888881", PERSON))
        .containsExactly(
            new Change(null, REJECTED, KYC_CHECK, rejectedAt),
            new Change(REJECTED, ON_HOLD, ONBOARDING_RULE_RERUN, heldAt));
  }

  @Test
  void recordsASeededOnboardingOnlyWhenItWasAbsent() {
    var seededAt = Instant.parse("2026-11-18T10:00:00Z");
    ClockHolder.setClock(Clock.fixed(seededAt, ZoneOffset.UTC));
    repository.insertOnboardingStatusIfAbsent(
        "60001019906", PERSON, PENDING, CHILD_ONBOARDING_STARTED);
    repository.saveOnboardingStatus("60001019906", PERSON, COMPLETED, KYC_CHECK);
    repository.insertOnboardingStatusIfAbsent(
        "60001019906", PERSON, PENDING, CHILD_ONBOARDING_STARTED);

    assertThat(statusChanges.of("60001019906", PERSON))
        .containsExactly(
            new Change(null, PENDING, CHILD_ONBOARDING_STARTED, seededAt),
            new Change(PENDING, COMPLETED, KYC_CHECK, seededAt));
  }

  @Test
  void keepsTheStatusChangesOfAPersonAndACompanyWithTheSameCodeApart() {
    repository.saveOnboardingStatus("14118923", LEGAL_ENTITY, ON_HOLD, KYB_CHECK);
    repository.saveOnboardingStatus("14118923", PERSON, COMPLETED, KYC_CHECK);

    assertThat(statusChanges.of("14118923", LEGAL_ENTITY))
        .extracting(Change::newStatus)
        .containsExactly(ON_HOLD);
  }

  private Company company(String registryCode) {
    return entityManager.persist(
        Company.builder().registryCode(registryCode).name("Test OÜ " + registryCode).build());
  }

  private Instant updatedAt(String code, PartyId.Type type) {
    return jdbcClient
        .sql("SELECT updated_at FROM savings_fund_onboarding WHERE code = :code AND type = :type")
        .param("code", code)
        .param("type", type.name())
        .query((rs, rowNum) -> rs.getTimestamp("updated_at").toInstant())
        .single();
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
    repository.saveOnboardingStatus("38888888811", PERSON, COMPLETED, KYC_CHECK);
    repository.saveOnboardingStatus("38888888822", PERSON, PENDING, KYC_CHECK);
    repository.saveOnboardingStatus("38888888833", PERSON, REJECTED, KYC_CHECK);

    assertThat(repository.isOnboardingCompleted("38888888811", PERSON)).isTrue();
    assertThat(repository.isOnboardingCompleted("38888888822", PERSON)).isFalse();
    assertThat(repository.isOnboardingCompleted("38888888833", PERSON)).isFalse();
    assertThat(repository.isOnboardingCompleted("37603135585", PERSON)).isFalse();
  }

  @Test
  void saveOnboardingStatus_insertsNewRecord() {
    repository.saveOnboardingStatus("38888888844", PERSON, PENDING, KYC_CHECK);

    assertThat(repository.findStatus("38888888844", PERSON)).contains(PENDING);
  }

  @Test
  void saveOnboardingStatus_updatesExistingRecord() {
    repository.saveOnboardingStatus("38888888855", PERSON, PENDING, KYC_CHECK);
    repository.saveOnboardingStatus("38888888855", PERSON, COMPLETED, KYC_CHECK);

    assertThat(repository.findStatus("38888888855", PERSON)).contains(COMPLETED);
  }

  @Test
  void saveOnboardingStatus_returnsEmptyWhenNoRecordExisted() {
    assertThat(repository.saveOnboardingStatus("87654321", LEGAL_ENTITY, PENDING, KYB_CHECK))
        .isEmpty();
  }

  @Test
  void saveOnboardingStatus_returnsThePreviousStatusWhenRecordExisted() {
    repository.saveOnboardingStatus("12121212", LEGAL_ENTITY, PENDING, KYB_CHECK);

    assertThat(repository.saveOnboardingStatus("12121212", LEGAL_ENTITY, COMPLETED, KYB_CHECK))
        .contains(PENDING);
  }

  @Test
  void findPersonCodes_returnsEveryOnboardedPersonRegardlessOfStatus() {
    repository.saveOnboardingStatus("60001019906", PERSON, COMPLETED, KYC_CHECK);
    repository.saveOnboardingStatus("38888888888", PERSON, PENDING, KYC_CHECK);
    repository.saveOnboardingStatus("14118923", LEGAL_ENTITY, COMPLETED, KYB_CHECK);

    assertThat(repository.findPersonCodes())
        .containsExactlyInAnyOrder("60001019906", "38888888888");
  }

  // Concurrent verifications re-screen the same waiting set. Taking the companies in a stable
  // order keeps their per-company locks in a consistent sequence.
  @Test
  void findPendingLegalEntityCodes_returnsOnlyPendingLegalEntitiesOrderedByCode() {
    repository.saveOnboardingStatus("33333333", LEGAL_ENTITY, PENDING, KYB_CHECK);
    repository.saveOnboardingStatus("11111111", LEGAL_ENTITY, PENDING, KYB_CHECK);
    repository.saveOnboardingStatus("22222222", LEGAL_ENTITY, COMPLETED, KYB_CHECK);
    repository.saveOnboardingStatus("44444444", LEGAL_ENTITY, PENDING, KYB_CHECK);
    repository.saveOnboardingStatus("38888888888", PERSON, PENDING, KYC_CHECK);

    assertThat(repository.findPendingLegalEntityCodes())
        .containsExactly("11111111", "33333333", "44444444");
  }

  @Test
  void saveOnboardingStatus_separatesPersonAndLegalEntity() {
    repository.saveOnboardingStatus("14118923", LEGAL_ENTITY, COMPLETED, KYB_CHECK);

    assertThat(repository.isOnboardingCompleted("14118923", LEGAL_ENTITY)).isTrue();
    assertThat(repository.isOnboardingCompleted("14118923", PERSON)).isFalse();
  }

  @Test
  void saveOnboardingStatus_stampsUpdatedAtWithTheClock() {
    var startedAt = Instant.parse("2026-08-01T10:00:00Z");
    ClockHolder.setClock(Clock.fixed(startedAt, ZoneOffset.UTC));

    repository.saveOnboardingStatus("38888888888", PERSON, PENDING, KYC_CHECK);

    assertThat(updatedAt("38888888888", PERSON)).isEqualTo(startedAt);
  }

  @Test
  void saveOnboardingStatus_movesUpdatedAtWhenTheStatusChanges() {
    var startedAt = Instant.parse("2026-08-01T10:00:00Z");
    var completedAt = Instant.parse("2026-08-05T10:00:00Z");
    ClockHolder.setClock(Clock.fixed(startedAt, ZoneOffset.UTC));
    repository.saveOnboardingStatus("38888888888", PERSON, PENDING, KYC_CHECK);

    ClockHolder.setClock(Clock.fixed(completedAt, ZoneOffset.UTC));
    repository.saveOnboardingStatus("38888888888", PERSON, COMPLETED, KYC_CHECK);

    assertThat(updatedAt("38888888888", PERSON)).isEqualTo(completedAt);
  }

  @Test
  void saveOnboardingStatus_movesUpdatedAtOnEveryWrite() {
    var completedAt = Instant.parse("2026-08-05T10:00:00Z");
    var rewrittenAt = Instant.parse("2026-08-20T10:00:00Z");
    ClockHolder.setClock(Clock.fixed(completedAt, ZoneOffset.UTC));
    repository.saveOnboardingStatus("38888888888", PERSON, COMPLETED, KYC_CHECK);

    ClockHolder.setClock(Clock.fixed(rewrittenAt, ZoneOffset.UTC));
    repository.saveOnboardingStatus("38888888888", PERSON, COMPLETED, KYC_CHECK);

    assertThat(updatedAt("38888888888", PERSON)).isEqualTo(rewrittenAt);
  }

  @Test
  void insertOnboardingStatusIfAbsent_stampsUpdatedAtWithTheClock() {
    var startedAt = Instant.parse("2026-08-01T10:00:00Z");
    ClockHolder.setClock(Clock.fixed(startedAt, ZoneOffset.UTC));

    repository.insertOnboardingStatusIfAbsent(
        "38888888888", PERSON, PENDING, CHILD_ONBOARDING_STARTED);

    assertThat(updatedAt("38888888888", PERSON)).isEqualTo(startedAt);
  }

  @Test
  void insertOnboardingStatusIfAbsent_insertsNewRecord() {
    repository.insertOnboardingStatusIfAbsent(
        "60001019906", PERSON, PENDING, CHILD_ONBOARDING_STARTED);

    assertThat(repository.findStatus("60001019906", PERSON)).contains(PENDING);
  }

  @Test
  void insertOnboardingStatusIfAbsent_doesNotOverwriteExistingRecord() {
    repository.saveOnboardingStatus("60001019906", PERSON, COMPLETED, KYC_CHECK);

    repository.insertOnboardingStatusIfAbsent(
        "60001019906", PERSON, PENDING, CHILD_ONBOARDING_STARTED);

    assertThat(repository.findStatus("60001019906", PERSON)).contains(COMPLETED);
  }
}
