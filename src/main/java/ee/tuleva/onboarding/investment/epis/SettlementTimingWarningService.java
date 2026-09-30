package ee.tuleva.onboarding.investment.epis;

import static ee.tuleva.onboarding.investment.epis.PevaRavaPhase.DONE;
import static ee.tuleva.onboarding.investment.epis.SettlementTimingWarning.Type.PEVA_DEADLINE_MISS;
import static ee.tuleva.onboarding.investment.epis.SettlementTimingWarning.Type.REBALANCE_GAP;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.FUND;
import static java.util.Comparator.naturalOrder;

import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocation;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocationRepository;
import ee.tuleva.onboarding.investment.transaction.SettlementDateCalculator;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SettlementTimingWarningService {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
  private static final List<TulevaFund> PEVA_RAVA_FUNDS =
      List.of(TulevaFund.TUK75, TulevaFund.TUK00);

  private final PevaRavaPeriodService periodService;
  private final SettlementDateCalculator settlementDateCalculator;
  private final ModelPortfolioAllocationRepository allocationRepository;
  private final Clock clock;

  public List<SettlementTimingWarning> activeWarnings() {
    var placedNow = clock.instant();
    return periodService
        .getCurrentPeriod(dateOf(placedNow))
        .map(
            period ->
                PEVA_RAVA_FUNDS.stream()
                    .flatMap(fund -> warningsForFund(period, fund, placedNow).stream())
                    .toList())
        .orElse(List.of());
  }

  public List<SettlementTimingWarning> activeWarnings(TulevaFund fund) {
    if (!PEVA_RAVA_FUNDS.contains(fund)) {
      return List.of();
    }
    var placedNow = clock.instant();
    return periodService
        .getCurrentPeriod(dateOf(placedNow))
        .map(period -> warningsForFund(period, fund, placedNow))
        .orElse(List.of());
  }

  private List<SettlementTimingWarning> warningsForFund(
      PevaRavaPeriod period, TulevaFund fund, Instant placedNow) {
    LocalDate execDate = period.cycle().execDate();
    if (period.phase() == DONE || dateOf(placedNow).isAfter(execDate)) {
      return List.of();
    }
    if (!period.timelineFor(fund).dActive()) {
      return List.of();
    }
    return worstFundSellSettlementDate(fund, placedNow)
        .map(sellSettlementDate -> fundWarnings(fund, placedNow, sellSettlementDate, execDate))
        .orElse(List.of());
  }

  private List<SettlementTimingWarning> fundWarnings(
      TulevaFund fund, Instant placedNow, LocalDate sellSettlementDate, LocalDate execDate) {
    List<SettlementTimingWarning> warnings = new ArrayList<>();
    if (sellSettlementDate.isAfter(execDate)) {
      warnings.add(
          new SettlementTimingWarning(
              PEVA_DEADLINE_MISS,
              fund,
              sellSettlementDate,
              execDate,
              "FUND sell placed today settles after PEVA/RAVA execution: fund="
                  + fund.getCode()
                  + ", sellSettlementDate="
                  + sellSettlementDate
                  + ", execDate="
                  + execDate));
    }
    LocalDate etfBuySettlementDate =
        settlementDateCalculator.calculateSettlementDate(placedNow, ETF, fund.getIsin());
    if (sellSettlementDate.isAfter(etfBuySettlementDate)) {
      warnings.add(
          new SettlementTimingWarning(
              REBALANCE_GAP,
              fund,
              sellSettlementDate,
              etfBuySettlementDate,
              "FUND sell settles after same-day ETF buy: fund="
                  + fund.getCode()
                  + ", sellSettlementDate="
                  + sellSettlementDate
                  + ", etfBuySettlementDate="
                  + etfBuySettlementDate));
    }
    return warnings;
  }

  private Optional<LocalDate> worstFundSellSettlementDate(TulevaFund fund, Instant placedNow) {
    return allocationRepository.findLatestByFundAsOf(fund, dateOf(placedNow)).stream()
        .filter(allocation -> allocation.getInstrumentType() == FUND)
        .map(ModelPortfolioAllocation::getIsin)
        .filter(Objects::nonNull)
        .map(isin -> settlementDateCalculator.calculateSettlementDate(placedNow, FUND, isin))
        .max(naturalOrder());
  }

  private LocalDate dateOf(Instant instant) {
    return LocalDate.ofInstant(instant, TALLINN);
  }
}
