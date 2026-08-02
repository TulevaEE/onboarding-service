package ee.tuleva.onboarding.analytics.transaction.unitowner;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.time.FixedClockConfig;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UnitOwnerSnapshotDateValidatorTest extends FixedClockConfig {

  @Mock private UnitOwnerRepository repository;

  @InjectMocks private UnitOwnerSnapshotDateValidator validator;

  private final LocalDate today = LocalDate.of(2020, 1, 1);

  @Test
  void acceptsToday() {
    given(repository.existsBySnapshotDate(today)).willReturn(false);

    assertThatCode(() -> validator.validate(today)).doesNotThrowAnyException();
  }

  @Test
  void acceptsAMissedSnapshotInsideTheBackfillWindow() {
    LocalDate fiveDaysAgo = today.minusDays(5);
    given(repository.existsBySnapshotDate(fiveDaysAgo)).willReturn(false);

    assertThatCode(() -> validator.validate(fiveDaysAgo)).doesNotThrowAnyException();
  }

  @Test
  void rejectsADateOlderThanTheBackfillWindow() {
    LocalDate sixDaysAgo = today.minusDays(6);

    assertThatThrownBy(() -> validator.validate(sixDaysAgo))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsAFutureDate() {
    LocalDate tomorrow = today.plusDays(1);

    assertThatThrownBy(() -> validator.validate(tomorrow))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsADateThatAlreadyHasASnapshot() {
    LocalDate yesterday = today.minusDays(1);
    given(repository.existsBySnapshotDate(yesterday)).willReturn(true);

    assertThatThrownBy(() -> validator.validate(yesterday))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
