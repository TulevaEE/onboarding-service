package ee.tuleva.onboarding.investment.transaction;

import ee.tuleva.onboarding.investment.calendar.DomicileCalendar;
import ee.tuleva.onboarding.investment.calendar.Target2Calendar;
import ee.tuleva.onboarding.investment.calendar.TradingCalendar;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocation;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocationRepository;
import ee.tuleva.onboarding.investment.portfolio.Provider;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementDateCalculator {

  private static final int ETF_SETTLEMENT_BUSINESS_DAYS = 2;
  private static final int FUND_SETTLEMENT_BUSINESS_DAYS = 5;

  private final Target2Calendar target2Calendar;
  private final DomicileCalendar domicileCalendar;
  private final ModelPortfolioAllocationRepository allocationRepository;

  public LocalDate calculateSettlementDate(
      LocalDate tradeDate, InstrumentType instrumentType, String isin) {
    int businessDays =
        switch (instrumentType) {
          case ETF -> ETF_SETTLEMENT_BUSINESS_DAYS;
          case FUND -> FUND_SETTLEMENT_BUSINESS_DAYS;
        };
    return addBusinessDays(tradeDate, instrumentType, isin, businessDays);
  }

  public LocalDate addBusinessDays(
      LocalDate tradeDate, InstrumentType instrumentType, String isin, int businessDays) {
    return calendarFor(instrumentType, isin, tradeDate).addBusinessDays(tradeDate, businessDays);
  }

  private TradingCalendar calendarFor(
      InstrumentType instrumentType, String isin, LocalDate tradeDate) {
    return switch (instrumentType) {
      case ETF -> target2Calendar;
      case FUND -> fundCalendar(isin, tradeDate);
    };
  }

  private TradingCalendar fundCalendar(String isin, LocalDate tradeDate) {
    return allocationRepository
        .findFirstByIsinAndProviderIsNotNullAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
            isin, tradeDate)
        .map(ModelPortfolioAllocation::getProvider)
        .map(Provider::getDomicile)
        .map(domicileCalendar::forDomicile)
        .orElseGet(
            () -> {
              log.warn("No provider found for fund, falling back to TARGET2: isin={}", isin);
              return target2Calendar;
            });
  }
}
