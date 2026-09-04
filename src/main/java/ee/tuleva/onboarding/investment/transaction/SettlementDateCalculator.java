package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;

import ee.tuleva.onboarding.instrument.InstrumentReference;
import ee.tuleva.onboarding.instrument.InstrumentReferenceService;
import ee.tuleva.onboarding.instrument.SettlementTerms;
import ee.tuleva.onboarding.investment.calendar.Domicile;
import ee.tuleva.onboarding.investment.calendar.DomicileCalendar;
import ee.tuleva.onboarding.investment.calendar.Target2Calendar;
import ee.tuleva.onboarding.investment.calendar.TradingCalendar;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocation;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocationRepository;
import ee.tuleva.onboarding.investment.portfolio.Provider;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementDateCalculator {

  private static final int ETF_SETTLEMENT_BUSINESS_DAYS = 2;
  private static final int FUND_SETTLEMENT_BUSINESS_DAYS = 4;
  private static final ZoneId DEFAULT_TRADE_ZONE = ZoneId.of(TIMEZONE);

  private final Target2Calendar target2Calendar;
  private final DomicileCalendar domicileCalendar;
  private final ModelPortfolioAllocationRepository allocationRepository;
  private final InstrumentReferenceService instrumentReferenceService;

  public LocalDate calculateSettlementDate(
      Instant submittedAt, InstrumentType instrumentType, String isin) {
    return instrumentReferenceService
        .settlementTerms(isin)
        .map(terms -> settlementFromTerms(submittedAt, instrumentType, isin, terms))
        .orElseGet(
            () ->
                flatSettlementDate(
                    submittedAt.atZone(DEFAULT_TRADE_ZONE).toLocalDate(), instrumentType, isin));
  }

  public LocalDate calculateSettlementDate(
      LocalDate acceptanceDate, InstrumentType instrumentType, String isin) {
    return instrumentReferenceService
        .settlementTerms(isin)
        .map(
            terms ->
                settleFrom(
                    dealingDay(acceptanceDate, instrumentType, isin), terms.daysFromAcceptance()))
        .orElseGet(() -> flatSettlementDate(acceptanceDate, instrumentType, isin));
  }

  public LocalDate addBusinessDays(
      LocalDate tradeDate, InstrumentType instrumentType, String isin, int businessDays) {
    return settleFrom(dealingDay(tradeDate, instrumentType, isin), businessDays);
  }

  private LocalDate dealingDay(LocalDate date, InstrumentType instrumentType, String isin) {
    return dealingCalendar(instrumentType, isin, date).nextOrSameBusinessDay(date);
  }

  private LocalDate settleFrom(LocalDate acceptanceDate, int businessDays) {
    return target2Calendar.addBusinessDays(acceptanceDate, businessDays);
  }

  private LocalDate flatSettlementDate(
      LocalDate tradeDate, InstrumentType instrumentType, String isin) {
    int businessDays =
        switch (instrumentType) {
          case ETF -> ETF_SETTLEMENT_BUSINESS_DAYS;
          case FUND -> FUND_SETTLEMENT_BUSINESS_DAYS;
        };
    return addBusinessDays(tradeDate, instrumentType, isin, businessDays);
  }

  private LocalDate settlementFromTerms(
      Instant submittedAt, InstrumentType instrumentType, String isin, SettlementTerms terms) {
    ZonedDateTime submitted = submittedAt.atZone(terms.cutoffZone());
    LocalDate submissionDate = submitted.toLocalDate();
    TradingCalendar dealing = dealingCalendar(instrumentType, isin, submissionDate);
    LocalDate acceptanceDate =
        submitted.toLocalTime().isAfter(terms.cutoffTime())
            ? dealing.addBusinessDays(submissionDate, 1)
            : dealing.nextOrSameBusinessDay(submissionDate);
    return settleFrom(acceptanceDate, terms.daysFromAcceptance());
  }

  private TradingCalendar dealingCalendar(
      InstrumentType instrumentType, String isin, LocalDate tradeDate) {
    return switch (instrumentType) {
      case ETF -> target2Calendar;
      case FUND -> fundCalendar(isin, tradeDate);
    };
  }

  private TradingCalendar fundCalendar(String isin, LocalDate tradeDate) {
    return instrumentDomicile(isin)
        .or(() -> providerDomicile(isin, tradeDate))
        .map(domicileCalendar::forDomicile)
        .orElseGet(
            () -> {
              log.warn("No domicile found for fund, falling back to TARGET2: isin={}", isin);
              return target2Calendar;
            });
  }

  private Optional<Domicile> instrumentDomicile(String isin) {
    return instrumentReferenceService
        .findByIsin(isin)
        .map(InstrumentReference::getCountry)
        .flatMap(Domicile::forCountryCode);
  }

  private Optional<Domicile> providerDomicile(String isin, LocalDate tradeDate) {
    return allocationRepository
        .findFirstByIsinAndProviderIsNotNullAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
            isin, tradeDate)
        .map(ModelPortfolioAllocation::getProvider)
        .map(Provider::getDomicile)
        .map(
            domicile -> {
              log.warn(
                  "Instrument has no supported country, using the provider's domicile: isin={}, domicile={}",
                  isin,
                  domicile);
              return domicile;
            });
  }
}
