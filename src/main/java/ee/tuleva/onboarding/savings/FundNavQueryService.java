package ee.tuleva.onboarding.savings;

import ee.tuleva.onboarding.savings.fund.nav.NavCalculation;
import ee.tuleva.onboarding.savings.fund.nav.NavReportRepository;
import ee.tuleva.onboarding.savings.fund.nav.NavReportRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FundNavQueryService {

  private static final String NAV_ACCOUNT_TYPE = "NAV";

  private static final List<String> FEE_BASE_ACCOUNT_TYPES =
      List.of("SECURITY", "CASH", "RECEIVABLES", "LIABILITY");

  private static final List<String> ASSET_ACCOUNT_TYPES =
      List.of("SECURITY", "CASH", "RECEIVABLES");

  private final NavReportRepository navReportRepository;

  public Optional<BigDecimal> findPublishedNavPerUnit(String fundCode, LocalDate navDate) {
    return navReportRepository.findPublishedNavPerUnit(navDate, fundCode, NAV_ACCOUNT_TYPE);
  }

  public Optional<BigDecimal> findLatestNavPerUnit(String fundCode, LocalDate navDate) {
    return navReportRepository.findLatestNavPerUnit(navDate, fundCode, NAV_ACCOUNT_TYPE);
  }

  public Optional<LocalDate> findLatestNavDateOnOrBefore(String fundCode, LocalDate asOfDate) {
    return navReportRepository.findLatestNavDateByFundAndAccountTypeOnOrBefore(
        fundCode, NAV_ACCOUNT_TYPE, asOfDate);
  }

  // The anchor for a reader of published sums: the newest date whose calculation actually went
  // out. findLatestNavDateOnOrBefore does not filter on published_at, so pairing it with
  // findPublishedCalculation silently yields nothing whenever the most recent calculation is still
  // unpublished, even though an earlier published one exists.
  public Optional<LocalDate> findLatestPublishedNavDateOnOrBefore(
      String fundCode, LocalDate asOfDate) {
    return navReportRepository.findLatestPublishedNavDateByFundOnOrBefore(fundCode, asOfDate);
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
    return sumForPublishedCalculation(fundCode, navDate, FEE_BASE_ACCOUNT_TYPES);
  }

  public Optional<BigDecimal> findAssetTotal(String fundCode, LocalDate navDate) {
    return sumForPublishedCalculation(fundCode, navDate, ASSET_ACCOUNT_TYPES);
  }

  public List<LocalDate> findPublishedNavDatesBetween(
      String fundCode, LocalDate from, LocalDate to) {
    return navReportRepository.findPublishedNavDatesBetween(fundCode, from, to);
  }

  public Optional<NavCalculation> findPublishedCalculation(String fundCode, LocalDate navDate) {
    return navReportRepository
        .findFirstByFundCodeAndNavDateAndPublishedAtIsNotNullOrderByPublishedAtDescIdDesc(
            fundCode, navDate)
        .map(NavReportRow::getCalculationId)
        .flatMap(calculationId -> calculation(fundCode, navDate, calculationId));
  }

  public Optional<NavCalculation> findLatestCalculation(String fundCode, LocalDate navDate) {
    return navReportRepository
        .findFirstByFundCodeAndNavDateOrderByIdDesc(fundCode, navDate)
        .map(NavReportRow::getCalculationId)
        .flatMap(calculationId -> calculation(fundCode, navDate, calculationId));
  }

  private Optional<NavCalculation> calculation(
      String fundCode, LocalDate navDate, UUID calculationId) {
    return navReportRepository
        .findLastWrittenAtByCalculationId(fundCode, navDate, calculationId)
        .map(
            calculatedAt ->
                new NavCalculation(
                    calculationId,
                    calculatedAt,
                    navReportRepository.findLinesByCalculationId(
                        fundCode, navDate, calculationId)));
  }

  private Optional<BigDecimal> sumForPublishedCalculation(
      String fundCode, LocalDate navDate, List<String> accountTypes) {
    if (!navReportRepository.existsPublishedByNavDateAndFundCode(navDate, fundCode)) {
      return Optional.empty();
    }
    return Optional.of(
        navReportRepository.sumPublishedCalculationMarketValueByAccountTypes(
            fundCode, navDate, accountTypes));
  }

  public BigDecimal findFeeAccrualLiabilities(String fundCode, LocalDate navDate) {
    return navReportRepository.sumPublishedMarketValueByAccountType(
        fundCode, navDate, "LIABILITY_FEE");
  }
}
