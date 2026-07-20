package ee.tuleva.onboarding.comparisons.benchmark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorldMarketBenchmarkServiceTest {

  private static final Instant NOW = Instant.parse("2026-07-16T10:00:00Z");
  private static final Instant UPDATED_AT = Instant.parse("2026-07-15T12:00:00Z");

  @Mock private FundValueRepository fundValueRepository;

  @Test
  void computesAnnualizedReturnForPureAcwiWindow() {
    givenAcwi(acwi("2021-07-15", "100"), acwi("2026-07-15", "200"));

    assertThat(service().getReturns())
        .containsExactly(worldMarketReturn(5, "0.148720", "2021-07-15", "2026-07-15", false));
  }

  @Test
  void computesCompositeReturnWhenWindowStartsBeforeEquityCapLifted() {
    givenAcwi(
        acwi("2016-07-15", "80"),
        acwi("2019-09-02", "90"),
        acwi("2021-07-15", "100"),
        acwi("2026-07-15", "200"));
    givenBonds(bond("2016-07-15", "50"), bond("2019-09-02", "55"));

    assertThat(service().getReturns())
        .containsExactly(
            worldMarketReturn(5, "0.148720", "2021-07-15", "2026-07-15", false),
            worldMarketReturn(10, "0.095361", "2016-07-15", "2026-07-15", true));
  }

  @Test
  void resolvesAnchorToLastValueWithinFourteenDayTolerance() {
    givenAcwi(acwi("2021-07-10", "100"), acwi("2026-07-15", "200"));

    assertThat(service().getReturns())
        .containsExactly(worldMarketReturn(5, "0.148285", "2021-07-10", "2026-07-15", false));
  }

  @Test
  void omitsWindowWhenAnchorIsOutsideTolerance() {
    givenAcwi(acwi("2021-06-01", "100"), acwi("2026-07-15", "200"));

    assertThat(service().getReturns()).isEmpty();
  }

  @Test
  void omitsCompositeWindowWhenBondLegIsMissing() {
    givenAcwi(
        acwi("2016-07-15", "80"),
        acwi("2019-09-02", "90"),
        acwi("2021-07-15", "100"),
        acwi("2026-07-15", "200"));
    givenBonds();

    assertThat(service().getReturns())
        .containsExactly(worldMarketReturn(5, "0.148720", "2021-07-15", "2026-07-15", false));
  }

  @Test
  void returnsNothingWhenNoAcwiDataStored() {
    givenAcwi();

    assertThat(service().getReturns()).isEmpty();
  }

  private WorldMarketBenchmarkService service() {
    return new WorldMarketBenchmarkService(fundValueRepository, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private void givenAcwi(FundValue... values) {
    given(fundValueRepository.findValuesBetweenDates(eq("MSCI_ACWI"), any(), any()))
        .willReturn(List.of(values));
  }

  private void givenBonds(FundValue... values) {
    given(fundValueRepository.findValuesBetweenDates(eq("EURO_AGGREGATE_BOND"), any(), any()))
        .willReturn(List.of(values));
  }

  private static WorldMarketReturn worldMarketReturn(
      int years, String annualizedReturn, String fromDate, String toDate, boolean composite) {
    return new WorldMarketReturn(
        years,
        new BigDecimal(annualizedReturn),
        LocalDate.parse(fromDate),
        LocalDate.parse(toDate),
        composite);
  }

  private static FundValue acwi(String date, String value) {
    return new FundValue(
        "MSCI_ACWI", LocalDate.parse(date), new BigDecimal(value), "MSCI", UPDATED_AT);
  }

  private static FundValue bond(String date, String value) {
    return new FundValue(
        "EURO_AGGREGATE_BOND", LocalDate.parse(date), new BigDecimal(value), "YAHOO", UPDATED_AT);
  }
}
