package ee.tuleva.onboarding.savings;

import ee.tuleva.onboarding.savings.fund.nav.NavReportAccountNames;
import ee.tuleva.onboarding.savings.fund.nav.NavReportRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FundNavQueryService {

  // Written by NavReportMapper.navRow on every published calculation.
  private static final String NAV_ACCOUNT_TYPE = "NAV";

  // Fee base per Tingimused 18.2.1: every asset less every non-fee liability.
  private static final List<String> FEE_BASE_ACCOUNT_TYPES =
      List.of("SECURITY", "CASH", "RECEIVABLES", "LIABILITY");

  private static final List<String> ASSET_ACCOUNT_TYPES =
      List.of("SECURITY", "CASH", "RECEIVABLES");

  private static final List<String> CUSTODIAN_SOURCED_ACCOUNT_TYPES =
      List.of("CASH", "RECEIVABLES", "LIABILITY");

  private final NavReportRepository navReportRepository;

  // The official NAV per unit: what everyone outside the calculation itself should read.
  public Optional<BigDecimal> findPublishedNavPerUnit(String fundCode, LocalDate navDate) {
    return navReportRepository.findPublishedNavPerUnit(navDate, fundCode, NAV_ACCOUNT_TYPE);
  }

  // The NAV per unit of the newest calculation, published or not, for the gates that run before
  // publication and therefore have to read the calculation they are gating.
  public Optional<BigDecimal> findLatestNavPerUnit(String fundCode, LocalDate navDate) {
    return navReportRepository.findLatestNavPerUnit(navDate, fundCode, NAV_ACCOUNT_TYPE);
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

  public Optional<BigDecimal> findFeeBaseComponentTotal(String fundCode, LocalDate navDate) {
    return sumForLatestCalculationIncludingUnpublished(fundCode, navDate, FEE_BASE_ACCOUNT_TYPES);
  }

  public Optional<BigDecimal> findAssetTotal(String fundCode, LocalDate navDate) {
    return sumForLatestCalculationIncludingUnpublished(fundCode, navDate, ASSET_ACCOUNT_TYPES);
  }

  public Optional<BigDecimal> findCustodianComparableTotal(String fundCode, LocalDate navDate) {
    if (!navReportRepository.existsByFundCodeAndNavDate(fundCode, navDate)) {
      return Optional.empty();
    }
    return Optional.of(
        navReportRepository.sumLatestCalculationMarketValueExcludingAccountNames(
            fundCode,
            navDate,
            CUSTODIAN_SOURCED_ACCOUNT_TYPES,
            NavReportAccountNames.NOT_SOURCED_FROM_CUSTODIAN));
  }

  private Optional<BigDecimal> sumForLatestCalculationIncludingUnpublished(
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
