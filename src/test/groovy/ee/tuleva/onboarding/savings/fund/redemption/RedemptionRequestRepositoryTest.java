package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.VERIFIED;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequestFixture.redemptionRequestFixture;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.HOURS;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
class RedemptionRequestRepositoryTest {

  private static final Instant CUTOFF = Instant.parse("2026-08-11T13:00:00Z");

  @Autowired RedemptionRequestRepository repository;
  @Autowired TestEntityManager entityManager;

  private Long userId;

  @BeforeEach
  void persistUser() {
    userId = entityManager.persistFlushFind(sampleUserNonMember().id(null).build()).getId();
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
  void excludesRequestReleasedFromReviewAfterTheCutoff() {
    repository.save(
        redemptionRequestFixture()
            .userId(userId)
            .status(VERIFIED)
            .requestedAt(CUTOFF.minus(7, DAYS))
            .reviewedAt(CUTOFF.plus(1, HOURS))
            .build());

    assertThat(repository.findAcceptedBefore(VERIFIED, CUTOFF)).isEmpty();
  }

  @Test
  void includesRequestReleasedFromReviewBeforeTheCutoff() {
    var request =
        repository.save(
            redemptionRequestFixture()
                .userId(userId)
                .status(VERIFIED)
                .requestedAt(CUTOFF.minus(7, DAYS))
                .reviewedAt(CUTOFF.minus(1, HOURS))
                .build());

    assertThat(repository.findAcceptedBefore(VERIFIED, CUTOFF)).containsExactly(request);
  }

  @Test
  void excludesRequestsInOtherStatuses() {
    repository.save(
        redemptionRequestFixture()
            .userId(userId)
            .status(RedemptionRequest.Status.IN_REVIEW)
            .requestedAt(CUTOFF.minus(7, DAYS))
            .build());

    assertThat(repository.findAcceptedBefore(VERIFIED, CUTOFF)).isEmpty();
  }
}
