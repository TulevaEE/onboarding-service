package ee.tuleva.onboarding.investment.risk;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.investment.risk.RiskIndicatorStatus.CHANGE_PENDING;
import static ee.tuleva.onboarding.investment.risk.RiskIndicatorStatus.STABLE;
import static ee.tuleva.onboarding.investment.risk.RiskIndicatorType.SRI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class RiskIndicatorPublicationRepositoryIT {

  @Autowired private RiskIndicatorPublicationRepository repository;

  @Test
  void persistsAndReadsBackAPublicationIncludingItsJsonDetails() {
    var saved = repository.save(publication(LocalDate.of(2026, 6, 30), 4, STABLE, true, 4));

    var found =
        repository.findByIndicatorTypeAndFundAndEvaluationDate(
            SRI, TKF100, LocalDate.of(2026, 6, 30));

    assertThat(found).isPresent();
    assertThat(found.get().getId()).isEqualTo(saved.getId());
    assertThat(found.get().getStatus()).isEqualTo(STABLE);
    assertThat(found.get().getNotified()).isTrue();
    assertThat(found.get().getNotifiedDisclosedClass()).isEqualTo(4);
    assertThat(found.get().getDetails()).containsEntry("rawStreakReferencePoints", "85");
    assertThat(found.get().getCreatedAt()).isNotNull();
  }

  @Test
  void theTransitionBaselineSkipsPublicationsThatNeverReachedSlack() {
    repository.save(publication(LocalDate.of(2026, 6, 28), 4, STABLE, true, 4));
    repository.save(publication(LocalDate.of(2026, 6, 29), 5, CHANGE_PENDING, false, null));

    var baseline =
        repository.findFirstByIndicatorTypeAndFundAndNotifiedTrueOrderByEvaluationDateDesc(
            SRI, TKF100);

    assertThat(baseline).isPresent();
    assertThat(baseline.get().getEvaluationDate()).isEqualTo(LocalDate.of(2026, 6, 28));
    assertThat(baseline.get().getPublishedClass()).isEqualTo(4);
  }

  @Test
  void aNullPublishedClassAndDisclosureSurviveTheRoundTrip() {
    repository.save(publication(LocalDate.of(2026, 6, 30), null, STABLE, false, null));

    var found =
        repository.findByIndicatorTypeAndFundAndEvaluationDate(
            SRI, TKF100, LocalDate.of(2026, 6, 30));

    assertThat(found.get().getPublishedClass()).isNull();
    assertThat(found.get().getNotifiedDisclosedClass()).isNull();
  }

  @Test
  void rejectsTwoPublicationsForTheSameIndicatorFundAndDate() {
    repository.saveAndFlush(publication(LocalDate.of(2026, 6, 30), 4, STABLE, true, 4));

    assertThatThrownBy(
            () ->
                repository.saveAndFlush(publication(LocalDate.of(2026, 6, 30), 5, STABLE, true, 5)))
        .isInstanceOf(Exception.class);
  }

  private static RiskIndicatorPublication publication(
      LocalDate evaluationDate,
      Integer publishedClass,
      RiskIndicatorStatus status,
      boolean notified,
      Integer notifiedDisclosedClass) {
    return RiskIndicatorPublication.builder()
        .indicatorType(SRI)
        .fund(TKF100)
        .evaluationDate(evaluationDate)
        .publishedClass(publishedClass)
        .rawLatestClass(publishedClass)
        .previousPublishedClass(null)
        .publishedSince(LocalDate.of(2026, 3, 14))
        .streakReferencePoints(85)
        .windowReferencePoints(85)
        .matchingReferencePoints(85)
        .status(status)
        .notified(notified)
        .notifiedDisclosedClass(notifiedDisclosedClass)
        .details(Map.of("rawStreakReferencePoints", "85"))
        .build();
  }
}
