package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionHoldReason.MANUAL;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionHoldReason.PEP;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionHoldReason.SANCTION;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.CANCELLED;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.FROZEN;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.PAYOUT_HELD;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.PROCESSED;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.REDEEMED;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.RESERVED;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.VERIFIED;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequestFixture.redemptionRequestFixture;
import static java.math.BigDecimal.ZERO;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.HOURS;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
class RedemptionRequestRepositoryTest {

  private static final Instant CUTOFF = Instant.parse("2026-08-11T13:00:00Z");
  private static final UUID BATCH = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID OTHER_BATCH = UUID.fromString("22222222-2222-2222-2222-222222222222");

  @Autowired RedemptionRequestRepository repository;
  @Autowired TestEntityManager entityManager;

  private Long userId;

  @BeforeEach
  void persistUser() {
    var user =
        sampleUserNonMember()
            .id(null)
            .personalCode("38812121008")
            .email("redemption-request-repository-test@tuleva.ee")
            .build();
    userId = entityManager.persistFlushFind(user).getId();
  }

  @Test
  void findsRequestRequestedBeforeTheCutoff() {
    var request =
        repository.save(
            redemptionRequestFixture()
                .userId(userId)
                .status(VERIFIED)
                .requestedAt(CUTOFF.minus(1, DAYS))
                .build());

    assertThat(repository.findAcceptedBefore(VERIFIED, CUTOFF)).containsExactly(request);
  }

  @Test
  void excludesRequestRequestedAfterTheCutoff() {
    repository.save(
        redemptionRequestFixture()
            .userId(userId)
            .status(VERIFIED)
            .requestedAt(CUTOFF.plus(1, HOURS))
            .build());

    assertThat(repository.findAcceptedBefore(VERIFIED, CUTOFF)).isEmpty();
  }

  @Test
  void excludesFrozenRequestRequeuedAfterTheCutoff() {
    repository.save(
        redemptionRequestFixture()
            .userId(userId)
            .status(VERIFIED)
            .requestedAt(CUTOFF.minus(7, DAYS))
            .holdReasons(Set.of(SANCTION))
            .reviewedAt(CUTOFF.plus(1, HOURS))
            .requeuedAt(CUTOFF.plus(1, HOURS))
            .build());

    assertThat(repository.findAcceptedBefore(VERIFIED, CUTOFF)).isEmpty();
  }

  @Test
  void includesFrozenRequestRequeuedBeforeTheCutoff() {
    var request =
        repository.save(
            redemptionRequestFixture()
                .userId(userId)
                .status(VERIFIED)
                .requestedAt(CUTOFF.minus(7, DAYS))
                .holdReasons(Set.of(SANCTION))
                .reviewedAt(CUTOFF.minus(1, HOURS))
                .requeuedAt(CUTOFF.minus(1, HOURS))
                .build());

    assertThat(repository.findAcceptedBefore(VERIFIED, CUTOFF)).containsExactly(request);
  }

  @Test
  void keepsTheDealingDateOfAFlaggedRequestWhoseHoldWasReleasedAfterTheCutoff() {
    var request =
        repository.save(
            redemptionRequestFixture()
                .userId(userId)
                .status(VERIFIED)
                .requestedAt(CUTOFF.minus(1, DAYS))
                .holdReasons(Set.of(PEP))
                .reviewedAt(CUTOFF.plus(1, HOURS))
                .build());

    assertThat(repository.findAcceptedBefore(VERIFIED, CUTOFF)).containsExactly(request);
  }

  @Test
  void excludesRequestsInOtherStatuses() {
    repository.save(
        redemptionRequestFixture()
            .userId(userId)
            .status(FROZEN)
            .requestedAt(CUTOFF.minus(7, DAYS))
            .build());

    assertThat(repository.findAcceptedBefore(VERIFIED, CUTOFF)).isEmpty();
  }

  @Test
  void findsActiveHoldsWhoseNotificationDidNotGoOut() {
    var unnotified =
        repository.save(
            redemptionRequestFixture()
                .userId(userId)
                .status(FROZEN)
                .holdReasons(Set.of(SANCTION))
                .build());
    repository.save(
        redemptionRequestFixture()
            .userId(userId)
            .status(PAYOUT_HELD)
            .holdReasons(Set.of(PEP))
            .holdNotifiedAt(CUTOFF)
            .build());
    repository.save(
        redemptionRequestFixture()
            .userId(userId)
            .status(VERIFIED)
            .holdReasons(Set.of(PEP))
            .reviewedAt(CUTOFF)
            .holdReleasedAt(CUTOFF)
            .build());
    repository.save(
        redemptionRequestFixture()
            .userId(userId)
            .status(CANCELLED)
            .holdReasons(Set.of(PEP))
            .build());
    repository.save(redemptionRequestFixture().userId(userId).status(VERIFIED).build());

    var statuses = List.of(RESERVED, FROZEN, VERIFIED, PAYOUT_HELD);
    assertThat(repository.findWithUnsentHoldNotification(statuses)).containsExactly(unnotified);
  }

  @Test
  void marksAHoldNotifiedOnlyOnce() {
    var request =
        repository.save(
            redemptionRequestFixture()
                .userId(userId)
                .status(FROZEN)
                .holdReasons(Set.of(SANCTION))
                .build());

    assertThat(repository.markHoldNotified(request.getId(), CUTOFF)).isEqualTo(1);
    assertThat(repository.markHoldNotified(request.getId(), CUTOFF.plus(1, HOURS))).isZero();

    entityManager.clear();
    assertThat(repository.findById(request.getId()).orElseThrow().getHoldNotifiedAt())
        .isEqualTo(CUTOFF);
  }

  @Test
  void markingAVerificationAttemptKeepsAHoldRecordedSinceTheRequestWasRead() {
    var request =
        repository.save(redemptionRequestFixture().userId(userId).status(RESERVED).build());
    holdSince(request, FROZEN, SANCTION);

    assertThat(repository.markVerificationAttempted(request.getId(), CUTOFF)).isEqualTo(1);

    entityManager.clear();
    var stored = repository.findById(request.getId()).orElseThrow();
    assertThat(stored.getVerificationAttemptedAt()).isEqualTo(CUTOFF);
    assertThat(stored.getStatus()).isEqualTo(FROZEN);
    assertThat(stored.getHoldReasons()).containsExactly(SANCTION);
  }

  @Test
  void markingAnErrorReasonKeepsAHoldRecordedSinceTheRequestWasRead() {
    var request =
        repository.save(redemptionRequestFixture().userId(userId).status(VERIFIED).build());
    holdSince(request, PAYOUT_HELD, MANUAL);

    assertThat(repository.markErrorReason(request.getId(), "bank down")).isEqualTo(1);

    entityManager.clear();
    var stored = repository.findById(request.getId()).orElseThrow();
    assertThat(stored.getErrorReason()).isEqualTo("bank down");
    assertThat(stored.getStatus()).isEqualTo(PAYOUT_HELD);
    assertThat(stored.getHoldReasons()).containsExactly(MANUAL);
  }

  private void holdSince(
      RedemptionRequest request, RedemptionRequest.Status status, RedemptionHoldReason reason) {
    var current = repository.findById(request.getId()).orElseThrow();
    current.setStatus(status);
    current.setHoldReasons(Set.of(reason));
    entityManager.flush();
    entityManager.clear();
  }

  @Test
  void assignsTheBatchToEveryRequestItFundsWhateverItsStatusBecameMeanwhile() {
    var verified =
        repository.save(redemptionRequestFixture().userId(userId).status(VERIFIED).build());
    var heldMeanwhile =
        repository.save(redemptionRequestFixture().userId(userId).status(PAYOUT_HELD).build());
    var other = repository.save(redemptionRequestFixture().userId(userId).status(VERIFIED).build());

    assertThat(repository.assignBatch(List.of(verified.getId(), heldMeanwhile.getId()), BATCH))
        .isEqualTo(2);

    entityManager.clear();
    assertThat(repository.findById(verified.getId()).orElseThrow().getBatchId()).isEqualTo(BATCH);
    assertThat(repository.findById(heldMeanwhile.getId()).orElseThrow().getBatchId())
        .isEqualTo(BATCH);
    assertThat(repository.findById(other.getId()).orElseThrow().getBatchId()).isNull();
  }

  @Test
  void sumsOnlyTheCashOfABatchStillHeldForReview() {
    repository.save(held(BATCH, "25.00"));
    repository.save(held(BATCH, "15.50"));
    repository.save(held(OTHER_BATCH, "100.00"));
    repository.save(
        redemptionRequestFixture()
            .userId(userId)
            .status(REDEEMED)
            .cashAmount(new BigDecimal("40.00"))
            .batchId(BATCH)
            .build());

    assertThat(repository.sumCashAmount(BATCH, PAYOUT_HELD)).isEqualByComparingTo("40.50");
  }

  @Test
  void aBatchWithNothingHeldSumsToZero() {
    assertThat(repository.sumCashAmount(BATCH, PAYOUT_HELD)).isEqualByComparingTo(ZERO);
  }

  private RedemptionRequest held(UUID batchId, String cashAmount) {
    return redemptionRequestFixture()
        .userId(userId)
        .status(PAYOUT_HELD)
        .cashAmount(new BigDecimal(cashAmount))
        .batchId(batchId)
        .build();
  }

  @Test
  void findsRequestsInAnyOfTheGivenStatuses() {
    var reserved =
        repository.save(redemptionRequestFixture().userId(userId).status(RESERVED).build());
    var frozen = repository.save(redemptionRequestFixture().userId(userId).status(FROZEN).build());
    repository.save(redemptionRequestFixture().userId(userId).status(PROCESSED).build());

    assertThat(repository.findByStatusIn(List.of(RESERVED, FROZEN)))
        .containsExactlyInAnyOrder(reserved, frozen);
  }
}
