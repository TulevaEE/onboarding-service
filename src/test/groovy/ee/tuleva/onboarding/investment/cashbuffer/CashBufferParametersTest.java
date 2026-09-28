package ee.tuleva.onboarding.investment.cashbuffer;

import static ee.tuleva.onboarding.investment.config.InvestmentParameter.CASH_BUFFER_DRIFT_CONSECUTIVE_RUNS;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.CASH_BUFFER_DRIFT_THRESHOLD;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.CASH_BUFFER_FLOOR;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.CASH_BUFFER_INFLOW_CREDIT;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.CASH_BUFFER_INFLOW_PERCENTILE;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.CASH_BUFFER_OUTFLOW_PERCENTILE;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.investment.config.InvestmentParameter;
import ee.tuleva.onboarding.investment.config.InvestmentParameterRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CashBufferParametersTest {

  private static final LocalDate AS_OF = LocalDate.of(2026, 10, 6);

  @Mock private InvestmentParameterRepository repository;
  @InjectMocks private CashBufferParameters parameters;

  @BeforeEach
  void nothingIsConfigured() {
    given(repository.findLatestValueIfPresent(any(InvestmentParameter.class), eq(TUK75), eq(AS_OF)))
        .willReturn(Optional.empty());
    given(repository.findLatestValueIfPresent(any(InvestmentParameter.class), eq(AS_OF)))
        .willReturn(Optional.empty());
  }

  @Test
  void resolvesEachParameterFromTheFundBeforeFallingBackToTheGlobalValue() {
    global(CASH_BUFFER_OUTFLOW_PERCENTILE, "0.95");
    global(CASH_BUFFER_INFLOW_PERCENTILE, "0.20");
    global(CASH_BUFFER_INFLOW_CREDIT, "0");
    fund(CASH_BUFFER_INFLOW_CREDIT, "0.1");
    fund(CASH_BUFFER_FLOOR, "24000.00");
    global(CASH_BUFFER_DRIFT_THRESHOLD, "50000");
    global(CASH_BUFFER_DRIFT_CONSECUTIVE_RUNS, "2");

    assertThat(parameters.missing(TUK75, AS_OF)).isEmpty();
    assertThat(parameters.resolve(TUK75, AS_OF))
        .isEqualTo(
            new ReviewRules(
                new BufferModel(
                    new BigDecimal("0.95"),
                    new BigDecimal("0.20"),
                    new BigDecimal("0.1"),
                    new BigDecimal("24000.00")),
                new DriftRule(new BigDecimal("50000"), 2)));
  }

  @Test
  void namesEveryParameterThatHasNeitherAFundNorAGlobalValue() {
    global(CASH_BUFFER_OUTFLOW_PERCENTILE, "0.95");
    global(CASH_BUFFER_INFLOW_PERCENTILE, "0.20");
    global(CASH_BUFFER_INFLOW_CREDIT, "0");
    global(CASH_BUFFER_DRIFT_CONSECUTIVE_RUNS, "2");

    assertThat(parameters.missing(TUK75, AS_OF))
        .containsExactly(CASH_BUFFER_FLOOR, CASH_BUFFER_DRIFT_THRESHOLD);
  }

  private void global(InvestmentParameter parameter, String value) {
    given(repository.findLatestValueIfPresent(parameter, AS_OF))
        .willReturn(Optional.of(new BigDecimal(value)));
  }

  private void fund(InvestmentParameter parameter, String value) {
    given(repository.findLatestValueIfPresent(parameter, TUK75, AS_OF))
        .willReturn(Optional.of(new BigDecimal(value)));
  }
}
