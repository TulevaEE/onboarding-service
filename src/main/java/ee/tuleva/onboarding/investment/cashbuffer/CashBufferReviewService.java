package ee.tuleva.onboarding.investment.cashbuffer;

import static ee.tuleva.onboarding.investment.cashbuffer.FundReviewOutcome.NotRunReason.FAILED;
import static ee.tuleva.onboarding.investment.cashbuffer.FundReviewOutcome.NotRunReason.MISSING_PARAMETERS;
import static ee.tuleva.onboarding.investment.cashbuffer.FundReviewOutcome.NotRunReason.NO_COMPLETE_MONTH_OF_FLOWS;
import static ee.tuleva.onboarding.investment.cashbuffer.FundReviewOutcome.NotRunReason.NO_FEE_ACCRUALS;
import static ee.tuleva.onboarding.investment.cashbuffer.FundReviewOutcome.NotRunReason.NO_RESERVE_CONFIGURED;
import static java.util.function.Predicate.not;

import ee.tuleva.onboarding.investment.cashbuffer.FundReviewOutcome.NotRun;
import ee.tuleva.onboarding.investment.cashbuffer.FundReviewOutcome.Reviewed;
import ee.tuleva.onboarding.investment.portfolio.FundLimitRepository;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
class CashBufferReviewService {

  private final CashBufferParameters parameters;
  private final FundLimitRepository fundLimitRepository;
  private final FlowWindowReader flowWindowReader;
  private final ChargedFeeAccruals chargedFeeAccruals;
  private final CashBufferReviewRepository repository;
  private final CashBufferReviewNotifier notifier;

  List<FundReviewOutcome> reviewAllFunds(YearMonth reviewMonth, LocalDate reviewedOn) {
    var outcomes =
        fundsSettlingWithThePensionRegistrar()
            .map(fund -> reviewOrSayWhyNot(fund, reviewMonth, reviewedOn))
            .toList();
    notifier.notify(reviewMonth, outcomes);
    return outcomes;
  }

  private static Stream<TulevaFund> fundsSettlingWithThePensionRegistrar() {
    return Arrays.stream(TulevaFund.values()).filter(not(TulevaFund::isSavingsFund));
  }

  private FundReviewOutcome reviewOrSayWhyNot(
      TulevaFund fund, YearMonth reviewMonth, LocalDate reviewedOn) {
    try {
      return review(fund, reviewMonth, reviewedOn);
    } catch (RuntimeException e) {
      log.error(
          "Cash buffer review failed: fund={}, reviewMonth={}, exception={}",
          fund,
          reviewMonth,
          e.getClass().getSimpleName(),
          e);
      return new NotRun(fund, FAILED, "exception=" + e.getClass().getSimpleName());
    }
  }

  private FundReviewOutcome review(TulevaFund fund, YearMonth reviewMonth, LocalDate reviewedOn) {
    var missing = parameters.missing(fund, reviewedOn);
    if (!missing.isEmpty()) {
      log.warn(
          "Cash buffer review skipped, parameters missing: fund={}, parameters={}", fund, missing);
      return new NotRun(fund, MISSING_PARAMETERS, "parameters=" + missing);
    }
    var configured =
        fundLimitRepository.findLatestByFundAsOf(fund, reviewedOn).flatMap(ConfiguredReserve::of);
    if (configured.isEmpty()) {
      return new NotRun(fund, NO_RESERVE_CONFIGURED, "asOf=" + reviewedOn);
    }
    var window = flowWindowReader.everyCompleteMonthThrough(fund, reviewMonth);
    if (window.isEmpty()) {
      return new NotRun(fund, NO_COMPLETE_MONTH_OF_FLOWS, "reviewMonth=" + reviewMonth);
    }
    var accruedFees = chargedFeeAccruals.accruedDuring(fund, reviewMonth);
    if (accruedFees.isEmpty()) {
      return new NotRun(fund, NO_FEE_ACCRUALS, "feeMonth=" + reviewMonth);
    }
    return reviewed(
        fund, reviewMonth, reviewedOn, window.get(), accruedFees.get(), configured.get());
  }

  private Reviewed reviewed(
      TulevaFund fund,
      YearMonth reviewMonth,
      LocalDate reviewedOn,
      FlowWindow window,
      BigDecimal accruedFees,
      ConfiguredReserve configured) {
    var rules = parameters.resolve(fund, reviewedOn);
    var recommendation = rules.bufferModel().recommend(window, accruedFees);
    var divergence = recommendation.recommended().subtract(configured.reserveSoft());
    var drift =
        rules.driftRule().judge(divergence, consecutiveDriftedRunsBefore(fund, reviewMonth));
    var review =
        new CashBufferReview(
            fund, reviewMonth, reviewedOn, window, recommendation, configured, drift);
    repository.save(review);
    return new Reviewed(review);
  }

  private int consecutiveDriftedRunsBefore(TulevaFund fund, YearMonth reviewMonth) {
    return repository
        .findByFundAndMonth(fund, reviewMonth.minusMonths(1))
        .map(previous -> previous.drift().consecutiveRuns())
        .orElse(0);
  }
}
