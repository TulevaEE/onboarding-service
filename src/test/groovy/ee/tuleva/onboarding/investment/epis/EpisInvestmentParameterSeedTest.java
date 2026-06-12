package ee.tuleva.onboarding.investment.epis;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.PEVA_RAVA_PAYMENT_LIMIT_BUFFER;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.PEVA_RAVA_PAYMENT_LIMIT_ROUNDING_STEP;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.PEVA_RAVA_TRADE_BUFFER_PERCENT;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.PEVA_RAVA_TRADE_ROUNDING_STEP;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.R16_BUFFER_PERCENT;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.R16_ROUNDING_STEP;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.config.InvestmentParameterRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(InvestmentParameterRepository.class)
class EpisInvestmentParameterSeedTest {

  private static final LocalDate AS_OF = LocalDate.of(2026, 6, 12);

  @Autowired private InvestmentParameterRepository repository;

  @Test
  void r16ParametersAreSeeded() {
    assertThat(repository.findLatestValue(R16_BUFFER_PERCENT, AS_OF))
        .isEqualByComparingTo(new BigDecimal("0.02"));
    assertThat(repository.findLatestValue(R16_ROUNDING_STEP, AS_OF))
        .isEqualByComparingTo(new BigDecimal("1000"));
  }

  @Test
  void pevaRavaPaymentLimitParametersAreSeeded() {
    assertThat(repository.findLatestValue(PEVA_RAVA_PAYMENT_LIMIT_BUFFER, TUK75, AS_OF))
        .isEqualByComparingTo(new BigDecimal("500000"));
    assertThat(repository.findLatestValue(PEVA_RAVA_PAYMENT_LIMIT_BUFFER, TUK00, AS_OF))
        .isEqualByComparingTo(new BigDecimal("200000"));
    assertThat(repository.findLatestValue(PEVA_RAVA_PAYMENT_LIMIT_ROUNDING_STEP, AS_OF))
        .isEqualByComparingTo(new BigDecimal("5000"));
  }

  @Test
  void pevaRavaTradeParametersAreSeeded() {
    assertThat(repository.findLatestValue(PEVA_RAVA_TRADE_BUFFER_PERCENT, AS_OF))
        .isEqualByComparingTo(new BigDecimal("0.02"));
    assertThat(repository.findLatestValue(PEVA_RAVA_TRADE_ROUNDING_STEP, AS_OF))
        .isEqualByComparingTo(new BigDecimal("1000"));
  }
}
