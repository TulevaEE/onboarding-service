package ee.tuleva.onboarding.comparisons.fundvalue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.fund.FundNavValues.NavPoint;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FundNavValuesAdapterTest {

  @Mock private FundValueQueries fundValueQueries;
  @InjectMocks private FundNavValuesAdapter adapter;

  private final FundValue fundValue =
      new FundValue(
          "EE0000000000",
          LocalDate.parse("2026-08-28"),
          new BigDecimal("1.2345"),
          "PROVIDER",
          Instant.parse("2026-08-28T15:20:00Z"));

  @Test
  void mapsTheLatestValueOnOrBeforeADate() {
    var date = LocalDate.parse("2026-08-29");
    given(fundValueQueries.getLatestValue("EE0000000000", date)).willReturn(Optional.of(fundValue));

    assertThat(adapter.latestValueOnOrBefore("EE0000000000", date))
        .contains(new NavPoint(LocalDate.parse("2026-08-28"), new BigDecimal("1.2345")));
  }

  @Test
  void mapsTheLastValue() {
    given(fundValueQueries.findLastValueForFund("EE0000000000")).willReturn(Optional.of(fundValue));

    assertThat(adapter.lastValue("EE0000000000"))
        .contains(new NavPoint(LocalDate.parse("2026-08-28"), new BigDecimal("1.2345")));
  }

  @Test
  void mapsTheValuesBetweenDates() {
    var start = LocalDate.parse("2026-08-01");
    var end = LocalDate.parse("2026-08-31");
    given(fundValueQueries.findValuesBetweenDates("EE0000000000", start, end))
        .willReturn(java.util.List.of(fundValue));

    assertThat(adapter.valuesBetween("EE0000000000", start, end))
        .containsExactly(new NavPoint(LocalDate.parse("2026-08-28"), new BigDecimal("1.2345")));
  }
}
