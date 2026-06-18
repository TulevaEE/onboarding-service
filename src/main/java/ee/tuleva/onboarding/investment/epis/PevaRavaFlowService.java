package ee.tuleva.onboarding.investment.epis;

import static ee.tuleva.onboarding.investment.config.InvestmentParameter.PEVA_RAVA_PAYMENT_LIMIT_BUFFER;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.PEVA_RAVA_PAYMENT_LIMIT_ROUNDING_STEP;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.PEVA_RAVA_TRADE_BUFFER_PERCENT;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.PEVA_RAVA_TRADE_ROUNDING_STEP;
import static ee.tuleva.onboarding.investment.epis.SummaryData.number;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.CEILING;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.config.InvestmentParameterRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PevaRavaFlowService {

  private static final List<TulevaFund> PEVA_RAVA_FUNDS =
      List.of(TulevaFund.TUK75, TulevaFund.TUK00);
  private static final BigDecimal MAX_FUND_AUM_EUR = new BigDecimal("500000000");

  private final EpisReportSummaryRepository summaryRepository;
  private final OwnFundNavProvider ownFundNavProvider;
  private final InvestmentParameterRepository parameterRepository;
  private final PevaRavaCycleRepository cycleRepository;
  private final PevaRavaPeriodService periodService;
  private final Clock clock;

  public Map<TulevaFund, PevaRavaFlows> calculateFlows() {
    return calculateFlows(LocalDate.now(clock));
  }

  public Map<TulevaFund, PevaRavaFlows> calculateFlows(LocalDate asOfDate) {
    Map<TulevaFund, PevaRavaFlows> flows = new LinkedHashMap<>();
    for (TulevaFund fund : PEVA_RAVA_FUNDS) {
      calculateFlows(fund, asOfDate).ifPresent(fundFlows -> flows.put(fund, fundFlows));
    }
    return flows;
  }

  private Optional<PevaRavaFlows> calculateFlows(TulevaFund fund, LocalDate asOfDate) {
    Optional<PevaRavaCycleEntity> activeCycle = activeCycle(asOfDate);
    if (activeCycle.isEmpty()) {
      return Optional.empty();
    }
    Optional<EpisReportSummary> r17 = summaryForReport(activeCycle.get().getR17ReportId(), fund);
    Optional<EpisReportSummary> r21 = summaryForReport(activeCycle.get().getR21ReportId(), fund);
    if (r17.isEmpty() && r21.isEmpty()) {
      return Optional.empty();
    }

    BigDecimal pikUnits = r17.map(summary -> number(summary.getData(), "pikUnits")).orElse(ZERO);
    BigDecimal switchingNetUnits =
        r17.map(summary -> number(summary.getData(), "switchingNetUnits")).orElse(ZERO);
    BigDecimal ravaUnits = r21.map(summary -> number(summary.getData(), "ravaUnits")).orElse(ZERO);

    BigDecimal nav = ownFundNavProvider.latestNav(fund, asOfDate);
    BigDecimal pikEur = pikUnits.multiply(nav);
    BigDecimal switchingNetEur = switchingNetUnits.multiply(nav);
    BigDecimal ravaEur = ravaUnits.multiply(nav);
    validateMagnitude(fund, pikEur, switchingNetEur, ravaEur);

    BigDecimal liquidityRequired = pikEur.add(ZERO.max(ravaEur.subtract(switchingNetEur)));
    BigDecimal grossOut = pikEur.add(ravaEur).add(ZERO.max(switchingNetEur.negate()));

    return Optional.of(
        new PevaRavaFlows(
            pikEur,
            switchingNetEur,
            ravaEur,
            liquidityRequired,
            grossOut,
            paymentLimit(fund, grossOut, asOfDate),
            tradeBufferedLiquidity(liquidityRequired, asOfDate)));
  }

  private BigDecimal paymentLimit(TulevaFund fund, BigDecimal grossOut, LocalDate asOfDate) {
    BigDecimal buffer =
        parameterRepository.findLatestValue(PEVA_RAVA_PAYMENT_LIMIT_BUFFER, fund, asOfDate);
    BigDecimal step =
        parameterRepository.findLatestValue(PEVA_RAVA_PAYMENT_LIMIT_ROUNDING_STEP, asOfDate);
    return roundUpToStep(grossOut.add(buffer), step);
  }

  private BigDecimal tradeBufferedLiquidity(BigDecimal liquidityRequired, LocalDate asOfDate) {
    BigDecimal bufferPercent =
        parameterRepository.findLatestValue(PEVA_RAVA_TRADE_BUFFER_PERCENT, asOfDate);
    BigDecimal step = parameterRepository.findLatestValue(PEVA_RAVA_TRADE_ROUNDING_STEP, asOfDate);
    return roundUpToStep(liquidityRequired.abs().multiply(ONE.add(bufferPercent)), step);
  }

  private static BigDecimal roundUpToStep(BigDecimal value, BigDecimal step) {
    return value.divide(step, 0, CEILING).multiply(step);
  }

  private static void validateMagnitude(
      TulevaFund fund, BigDecimal pikEur, BigDecimal switchingNetEur, BigDecimal ravaEur) {
    if (pikEur.compareTo(MAX_FUND_AUM_EUR) > 0
        || switchingNetEur.abs().compareTo(MAX_FUND_AUM_EUR) > 0
        || ravaEur.compareTo(MAX_FUND_AUM_EUR) > 0) {
      throw new IllegalStateException(
          "PEVA/RAVA EUR amounts implausibly large: fund="
              + fund.getCode()
              + ", pikEur="
              + pikEur
              + ", switchingNetEur="
              + switchingNetEur
              + ", ravaEur="
              + ravaEur);
    }
  }

  private Optional<PevaRavaCycleEntity> activeCycle(LocalDate asOfDate) {
    return periodService
        .getCurrentPeriod(asOfDate)
        .flatMap(period -> cycleRepository.findByExecDate(period.cycle().execDate()));
  }

  private Optional<EpisReportSummary> summaryForReport(@Nullable Long reportId, TulevaFund fund) {
    if (reportId == null) {
      return Optional.empty();
    }
    return summaryRepository.findByReportIdAndFund(reportId, fund);
  }
}
