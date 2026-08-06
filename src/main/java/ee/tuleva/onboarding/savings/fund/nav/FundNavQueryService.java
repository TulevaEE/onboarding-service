package ee.tuleva.onboarding.savings.fund.nav;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// Reads aggregated NAV values from nav_report. Tuleva-internal source of truth for fund-level
// NAV per unit. Used by tracking-difference checks so they don't depend on index_values, which
// is the channel for external feeds (PENSIONIKESKUS, MSCI, etc.) and lags by ~1 day for pillar 2.
@Service
@RequiredArgsConstructor
public class FundNavQueryService {

  // Matches NavReportMapper.navRow which writes account_type='NAV' on every published calculation.
  private static final String NAV_ACCOUNT_TYPE = "NAV";

  private final NavReportRepository navReportRepository;

  public Optional<BigDecimal> findNavPerUnit(String fundCode, LocalDate navDate) {
    return navReportRepository
        .findFirstByFundCodeAndNavDateAndAccountType(fundCode, navDate, NAV_ACCOUNT_TYPE)
        .map(NavReportRow::getMarketPrice);
  }

  public Optional<LocalDate> findLatestNavDateOnOrBefore(String fundCode, LocalDate asOfDate) {
    return navReportRepository.findLatestNavDateByFundAndAccountTypeOnOrBefore(
        fundCode, NAV_ACCOUNT_TYPE, asOfDate);
  }

  public BigDecimal findAum(String fundCode, LocalDate navDate) {
    return navReportRepository.sumPublishedMarketValueByAccountType(fundCode, navDate, "UNITS");
  }

  public BigDecimal findSecuritiesTotalValue(String fundCode, LocalDate navDate) {
    return navReportRepository.sumPublishedMarketValueByAccountType(fundCode, navDate, "SECURITY");
  }

  public BigDecimal findCashValue(String fundCode, LocalDate navDate) {
    return navReportRepository.sumPublishedMarketValueByAccountType(fundCode, navDate, "CASH");
  }

  // The fee base is every asset less every non-fee liability, per Tingimused 18.2.1. Summing these
  // four account types over the latest calculation for the date reproduces NavCalculationService's
  // feeBaseValue exactly, because NavReportMapper writes each term as its own row and negates
  // liabilities. Deliberately not published-only: an unpublished calculation still has to have
  // charged the right base, and falling back to an older calculation would compare across dates.
  public Optional<BigDecimal> findFeeBaseComponentTotal(String fundCode, LocalDate navDate) {
    return sumForLatestCalculation(
        fundCode, navDate, List.of("SECURITY", "CASH", "RECEIVABLES", "LIABILITY"));
  }

  // What the custodian position report is the source of truth for: cash and unsettled trades.
  // Excluding the register-sourced and manually-adjusted rows makes the remainder comparable to
  // investment_fund_position, so a custodian row the ledger never recognised shows up as a
  // difference instead of hiding inside a fee base that recomputes consistently from itself.
  public Optional<BigDecimal> findCustodianComparableTotal(String fundCode, LocalDate navDate) {
    if (!navReportRepository.existsByFundCodeAndNavDate(fundCode, navDate)) {
      return Optional.empty();
    }
    return Optional.of(
        navReportRepository.sumLatestCalculationMarketValueExcludingAccountNames(
            fundCode,
            navDate,
            List.of("CASH", "RECEIVABLES", "LIABILITY"),
            NavReportAccountNames.NOT_SOURCED_FROM_CUSTODIAN));
  }

  private Optional<BigDecimal> sumForLatestCalculation(
      String fundCode, LocalDate navDate, List<String> accountTypes) {
    if (!navReportRepository.existsByFundCodeAndNavDate(fundCode, navDate)) {
      return Optional.empty();
    }
    return Optional.of(
        navReportRepository.sumLatestCalculationMarketValueByAccountTypes(
            fundCode, navDate, accountTypes));
  }

  public BigDecimal findFeeAccrualLiabilities(String fundCode, LocalDate navDate) {
    return navReportRepository.sumPublishedMarketValueByAccountType(
        fundCode, navDate, "LIABILITY_FEE");
  }
}
