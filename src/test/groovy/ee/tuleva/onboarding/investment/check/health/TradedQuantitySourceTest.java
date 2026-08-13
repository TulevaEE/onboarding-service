package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.investment.transaction.ExecutedQuantitySummary;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TradedQuantitySourceTest {

  private static final LocalDate NAV_DATE = LocalDate.of(2026, 4, 15);
  private static final LocalDate PREVIOUS_NAV_DATE = LocalDate.of(2026, 4, 14);

  @Mock private TransactionExecutionRepository executionRepository;

  @InjectMocks private TradedQuantitySource source;

  @Test
  void looksUpTradesTheCustodianReportedAfterThePreviousNavDateAndUpToTheNavDate() {
    given(
            executionRepository.sumExecutedQuantitiesByIsin(
                TUK75.getCode(), PREVIOUS_NAV_DATE, NAV_DATE))
        .willReturn(
            List.of(
                summary("IE0009FT4LX4", new BigDecimal("1500"), new BigDecimal("200")),
                summary("IE00BFG1TM61", ZERO, new BigDecimal("900"))));

    var traded = source.resolve(TUK75, PREVIOUS_NAV_DATE, NAV_DATE);

    assertThat(traded)
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "IE0009FT4LX4",
                new TradedQuantity(new BigDecimal("1500"), new BigDecimal("200")),
                "IE00BFG1TM61",
                new TradedQuantity(ZERO, new BigDecimal("900"))));
  }

  @Test
  void returnsEmptyMapWhenNoExecutions() {
    given(executionRepository.sumExecutedQuantitiesByIsin(anyString(), any(), any()))
        .willReturn(List.of());

    assertThat(source.resolve(TUK75, PREVIOUS_NAV_DATE, NAV_DATE)).isEmpty();
  }

  @Test
  void treatsMissingSideTotalsAsZero() {
    given(executionRepository.sumExecutedQuantitiesByIsin(anyString(), any(), any()))
        .willReturn(List.of(summary("IE0009FT4LX4", null, null)));

    assertThat(source.resolve(TUK75, PREVIOUS_NAV_DATE, NAV_DATE))
        .containsExactly(Map.entry("IE0009FT4LX4", TradedQuantity.NONE));
  }

  private ExecutedQuantitySummary summary(String isin, BigDecimal bought, BigDecimal sold) {
    return new ExecutedQuantitySummary() {
      @Override
      public String getIsin() {
        return isin;
      }

      @Override
      public BigDecimal getBought() {
        return bought;
      }

      @Override
      public BigDecimal getSold() {
        return sold;
      }
    };
  }
}
