package ee.tuleva.onboarding.investment.fees;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeeMonthResolverTest {

  @Mock private AumRepository aumRepository;

  @InjectMocks private FeeMonthResolver resolver;

  @Test
  void resolveFeeMonth_returnsCurrentMonth_whenBeforeLastBusinessDay() {
    LocalDate date = LocalDate.of(2025, 1, 15);
    LocalDate lastBusinessDay = LocalDate.of(2025, 1, 31);

    when(aumRepository.getLastAumDateInMonth(LocalDate.of(2025, 1, 1))).thenReturn(lastBusinessDay);

    LocalDate result = resolver.resolveFeeMonth(date);

    assertThat(result).isEqualTo(LocalDate.of(2025, 1, 1));
  }

  @Test
  void resolveFeeMonth_returnsNextMonth_whenAfterLastBusinessDay() {
    LocalDate saturday = LocalDate.of(2025, 1, 25);
    LocalDate lastBusinessDay = LocalDate.of(2025, 1, 24);

    when(aumRepository.getLastAumDateInMonth(LocalDate.of(2025, 1, 1))).thenReturn(lastBusinessDay);

    LocalDate result = resolver.resolveFeeMonth(saturday);

    assertThat(result).isEqualTo(LocalDate.of(2025, 2, 1));
  }

  @Test
  void resolveFeeMonth_returnsCurrentMonth_whenOnLastBusinessDay() {
    LocalDate date = LocalDate.of(2025, 1, 31);
    LocalDate lastBusinessDay = LocalDate.of(2025, 1, 31);

    when(aumRepository.getLastAumDateInMonth(LocalDate.of(2025, 1, 1))).thenReturn(lastBusinessDay);

    LocalDate result = resolver.resolveFeeMonth(date);

    assertThat(result).isEqualTo(LocalDate.of(2025, 1, 1));
  }

  @Test
  void resolveFeeMonth_returnsCurrentMonth_whenNoLastBusinessDayFound() {
    LocalDate date = LocalDate.of(2025, 1, 15);

    when(aumRepository.getLastAumDateInMonth(LocalDate.of(2025, 1, 1))).thenReturn(null);

    LocalDate result = resolver.resolveFeeMonth(date);

    assertThat(result).isEqualTo(LocalDate.of(2025, 1, 1));
  }

  @Test
  void resolveFeeMonth_handlesMonthEndWeekend() {
    LocalDate sunday = LocalDate.of(2025, 8, 31);
    LocalDate lastBusinessDay = LocalDate.of(2025, 8, 29);

    when(aumRepository.getLastAumDateInMonth(LocalDate.of(2025, 8, 1))).thenReturn(lastBusinessDay);

    LocalDate result = resolver.resolveFeeMonth(sunday);

    assertThat(result).isEqualTo(LocalDate.of(2025, 9, 1));
  }
}
