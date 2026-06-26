package ee.tuleva.onboarding.investment.epis;

import static ee.tuleva.onboarding.investment.epis.PevaRavaPhase.DONE;
import static ee.tuleva.onboarding.investment.epis.SettlementTimingWarning.Type.PEVA_DEADLINE_MISS;
import static ee.tuleva.onboarding.investment.epis.SettlementTimingWarning.Type.REBALANCE_GAP;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.FUND;
import static java.util.Comparator.naturalOrder;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocation;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocationRepository;
import ee.tuleva.onboarding.investment.transaction.SettlementDateCalculator;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SettlementTimingWarningService {

  private static final List<TulevaFund> PEVA_RAVA_FUNDS =
      List.of(TulevaFund.TUK75, TulevaFund.TUK00);

  private final PevaRavaPeriodService periodService;
  private final SettlementDateCalculator settlementDateCalculator;
  private final ModelPortfolioAllocationRepository allocationRepository;
  private final Clock clock;

  public List<SettlementTimingWarning> activeWarnings() {
    LocalDate today = LocalDate.now(clock);
    return periodService
        .getCurrentPeriod(today)
        .map(period -> warnings(period, today))
        .orElse(List.of());
  }

  private List<SettlementTimingWarning> warnings(PevaRavaPeriod period, LocalDate today) {
    LocalDate execDate = period.cycle().execDate();
    if (period.phase() == DONE || today.isAfter(execDate)) {
      return List.of();
    }
    List<SettlementTimingWarning> warnings = new ArrayList<>();
    for (TulevaFund fund : PEVA_RAVA_FUNDS) {
      if (!period.timelineFor(fund).dActive()) {
        continue;
      }
      worstFundSellSettlementDate(fund, today)
          .ifPresent(
              sellSettlementDate ->
                  warnings.addAll(fundWarnings(fund, today, sellSettlementDate, execDate)));
    }
    return warnings;
  }

  private List<SettlementTimingWarning> fundWarnings(
      TulevaFund fund, LocalDate today, LocalDate sellSettlementDate, LocalDate execDate) {
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
        settlementDateCalculator.calculateSettlementDate(today, ETF, fund.getIsin());
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

  private Optional<LocalDate> worstFundSellSettlementDate(TulevaFund fund, LocalDate today) {
    return allocationRepository.findLatestByFundAsOf(fund, today).stream()
        .filter(allocation -> allocation.getInstrumentType() == FUND)
        .map(ModelPortfolioAllocation::getIsin)
        .filter(Objects::nonNull)
        .map(isin -> settlementDateCalculator.calculateSettlementDate(today, FUND, isin))
        .max(naturalOrder());
  }
}
