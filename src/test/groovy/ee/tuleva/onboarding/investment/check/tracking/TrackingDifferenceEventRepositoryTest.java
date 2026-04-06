package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.check.tracking.TrackingCheckType.MODEL_PORTFOLIO;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class TrackingDifferenceEventRepositoryTest {

  @Autowired TrackingDifferenceEventRepository repository;
  @Autowired EntityManager entityManager;

  @Test
  void savesAndRetrievesEvent() {
    var checkDate = LocalDate.of(2026, 4, 3);
    var event = event(checkDate, true, 1);

    repository.save(event);

    var found = repository.findAll();
    assertThat(found)
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.getFund()).isEqualTo(TUK75);
              assertThat(e.getCheckDate()).isEqualTo(checkDate);
              assertThat(e.getCheckType()).isEqualTo(MODEL_PORTFOLIO);
              assertThat(e.isBreach()).isTrue();
              assertThat(e.getConsecutiveBreachDays()).isEqualTo(1);
              assertThat(e.getTrackingDifference())
                  .isEqualByComparingTo(new BigDecimal("0.001500"));
            });
  }

  @Test
  void deletesExistingEventOnRerun() {
    var checkDate = LocalDate.of(2026, 4, 3);
    repository.save(event(checkDate, true, 1));

    repository.deleteByFundAndCheckDateAndCheckType(TUK75, checkDate, MODEL_PORTFOLIO);
    entityManager.flush();
    repository.save(event(checkDate, false, 0));

    var found = repository.findAll();
    assertThat(found).singleElement().satisfies(e -> assertThat(e.isBreach()).isFalse());
  }

  @Test
  void findsMostRecentEventsInOrder() {
    repository.save(event(LocalDate.of(2026, 4, 1), true, 1));
    repository.save(event(LocalDate.of(2026, 4, 2), true, 2));
    repository.save(event(LocalDate.of(2026, 4, 3), true, 3));

    var recent =
        repository.findMostRecentEvents(TUK75, MODEL_PORTFOLIO, LocalDate.of(2026, 4, 4), 3);

    assertThat(recent).hasSize(3);
    assertThat(recent.get(0).getCheckDate()).isEqualTo(LocalDate.of(2026, 4, 3));
    assertThat(recent.get(1).getCheckDate()).isEqualTo(LocalDate.of(2026, 4, 2));
    assertThat(recent.get(2).getCheckDate()).isEqualTo(LocalDate.of(2026, 4, 1));
  }

  @Test
  void findsMostRecentEventsExcludesCheckDate() {
    repository.save(event(LocalDate.of(2026, 4, 3), true, 1));

    var recent =
        repository.findMostRecentEvents(TUK75, MODEL_PORTFOLIO, LocalDate.of(2026, 4, 3), 3);

    assertThat(recent).isEmpty();
  }

  private TrackingDifferenceEvent event(
      LocalDate checkDate, boolean breach, int consecutiveBreachDays) {
    return TrackingDifferenceEvent.builder()
        .fund(TUK75)
        .checkDate(checkDate)
        .checkType(MODEL_PORTFOLIO)
        .trackingDifference(new BigDecimal("0.001500"))
        .fundReturn(new BigDecimal("0.010000"))
        .benchmarkReturn(new BigDecimal("0.008500"))
        .breach(breach)
        .consecutiveBreachDays(consecutiveBreachDays)
        .result(Map.of("test", "data"))
        .build();
  }
}
