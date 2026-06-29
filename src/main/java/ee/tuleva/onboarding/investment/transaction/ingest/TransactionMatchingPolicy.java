package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.config.InvestmentParameter.TRANSACTION_MATCHING_ETF_QUANTITY_TOLERANCE;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.TRANSACTION_MATCHING_EXECUTION_PRICE_CONSISTENCY_TOLERANCE;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.TRANSACTION_MATCHING_FUND_BUY_AMOUNT_TOLERANCE;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.TRANSACTION_MATCHING_FUND_SELL_QUANTITY_TOLERANCE;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.TRANSACTION_MATCHING_NEAR_MISS_MULTIPLIER;

import ee.tuleva.onboarding.investment.config.InvestmentParameterRepository;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

@Component
@NullMarked
@RequiredArgsConstructor
class TransactionMatchingPolicy {

  private final InvestmentParameterRepository parameterRepository;
  private final Clock clock;

  TransactionMatchingProperties current() {
    LocalDate asOf = LocalDate.now(clock);
    return new TransactionMatchingProperties(
        parameterRepository.findLatestValue(TRANSACTION_MATCHING_ETF_QUANTITY_TOLERANCE, asOf),
        parameterRepository.findLatestValue(TRANSACTION_MATCHING_FUND_BUY_AMOUNT_TOLERANCE, asOf),
        parameterRepository.findLatestValue(
            TRANSACTION_MATCHING_FUND_SELL_QUANTITY_TOLERANCE, asOf),
        parameterRepository.findLatestValue(TRANSACTION_MATCHING_NEAR_MISS_MULTIPLIER, asOf),
        parameterRepository.findLatestValue(
            TRANSACTION_MATCHING_EXECUTION_PRICE_CONSISTENCY_TOLERANCE, asOf));
  }
}
