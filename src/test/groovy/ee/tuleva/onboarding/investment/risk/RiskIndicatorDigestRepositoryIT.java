package ee.tuleva.onboarding.investment.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@DataJpaTest
class RiskIndicatorDigestRepositoryIT {

  private static final LocalDate AUGUST = LocalDate.of(2026, 8, 1);

  @Autowired private RiskIndicatorDigestRepository repository;

  @Test
  void persistsAndReadsBackAMonthMarker() {
    repository.save(RiskIndicatorDigest.builder().digestMonth(AUGUST).complete(false).build());

    var found = repository.findByDigestMonth(AUGUST);

    assertThat(found).isPresent();
    assertThat(found.get().getComplete()).isFalse();
    assertThat(found.get().getSentAt()).isNotNull();
    assertThat(found.get().getVersion()).isNotNull();
  }

  @Test
  void theUniqueMonthIsWhatStopsASecondFirstSend() {
    repository.saveAndFlush(RiskIndicatorDigest.builder().digestMonth(AUGUST).build());

    assertThatThrownBy(
            () ->
                repository.saveAndFlush(RiskIndicatorDigest.builder().digestMonth(AUGUST).build()))
        .isInstanceOf(Exception.class);
  }

  @Test
  void aStaleUpgradeOfTheSameMonthLosesOnTheVersion() {
    var stored =
        repository.saveAndFlush(
            RiskIndicatorDigest.builder().digestMonth(AUGUST).complete(false).build());

    var oneInstance =
        RiskIndicatorDigest.builder()
            .id(stored.getId())
            .digestMonth(AUGUST)
            .complete(true)
            .version(stored.getVersion())
            .sentAt(stored.getSentAt())
            .build();
    var otherInstance =
        RiskIndicatorDigest.builder()
            .id(stored.getId())
            .digestMonth(AUGUST)
            .complete(true)
            .version(stored.getVersion())
            .sentAt(stored.getSentAt())
            .build();

    repository.saveAndFlush(oneInstance);

    assertThatThrownBy(() -> repository.saveAndFlush(otherInstance))
        .isInstanceOf(ObjectOptimisticLockingFailureException.class);
  }
}
