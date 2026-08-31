package ee.tuleva.onboarding.investment.check.fee;

import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.fees.FeeBaseValue;
import ee.tuleva.onboarding.investment.fees.FeeType;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import ee.tuleva.onboarding.ledger.SystemAccount;
import ee.tuleva.onboarding.savings.FundNavQueryService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class ExpectedFeeBases {

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");

  private final FundNavQueryService fundNavQueryService;
  private final NavLedgerRepository navLedgerRepository;
  private final PublicHolidays publicHolidays;
  private final LocalDate depotAssetBaseFrom;

  ExpectedFeeBases(
      FundNavQueryService fundNavQueryService,
      NavLedgerRepository navLedgerRepository,
      PublicHolidays publicHolidays,
      @Value("${investment.fee-check.depot-asset-base-from:2026-08-15}")
          LocalDate depotAssetBaseFrom) {
    this.fundNavQueryService = fundNavQueryService;
    this.navLedgerRepository = navLedgerRepository;
    this.publicHolidays = publicHolidays;
    this.depotAssetBaseFrom = depotAssetBaseFrom;
  }

  Optional<Map<FeeType, BigDecimal>> expectedBases(
      TulevaFund fund, List<FeeBaseValue> bases, LocalDate date) {
    var expected = new EnumMap<FeeType, BigDecimal>(FeeType.class);
    for (var base : bases) {
      var value = expectedBase(fund, base.feeType(), date);
      if (value.isEmpty()) {
        return Optional.empty();
      }
      expected.put(base.feeType(), value.get());
    }
    return Optional.of(expected);
  }

  private Optional<BigDecimal> expectedBase(TulevaFund fund, FeeType feeType, LocalDate date) {
    if (chargesDepotOnAssetValue(feeType, date)) {
      var total = fundNavQueryService.findAssetTotal(fund.getCode(), date);
      if (total.isEmpty()) {
        return Optional.empty();
      }
      return Optional.of(total.get().add(assetSideBlackrockAdjustment(fund, date)));
    }
    var total = fundNavQueryService.findFeeBaseComponentTotal(fund.getCode(), date);
    if (total.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(total.get().add(navFeeBaseBlackrockAdjustment(fund, date)));
  }

  private boolean chargesDepotOnAssetValue(FeeType feeType, LocalDate accrualDate) {
    return feeType == FeeType.DEPOT && !accrualDate.isBefore(depotAssetBaseFrom);
  }

  private BigDecimal navFeeBaseBlackrockAdjustment(TulevaFund fund, LocalDate positionReportDate) {
    return fund.isSavingsFund()
        ? blackrockAdjustmentMissingFromNavReport(fund, positionReportDate)
        : ZERO;
  }

  private BigDecimal assetSideBlackrockAdjustment(TulevaFund fund, LocalDate positionReportDate) {
    var adjustment = blackrockAdjustmentMissingFromNavReport(fund, positionReportDate);
    return fund.isSavingsFund() ? adjustment : adjustment.min(ZERO);
  }

  private BigDecimal blackrockAdjustmentMissingFromNavReport(
      TulevaFund fund, LocalDate positionReportDate) {
    var balance =
        navLedgerRepository.getSystemAccountBalanceBefore(
            SystemAccount.BLACKROCK_ADJUSTMENT.getAccountName(fund),
            navCutoffThatChargedTheFee(fund, positionReportDate));
    return balance == null ? ZERO : balance;
  }

  private Instant navCutoffThatChargedTheFee(TulevaFund fund, LocalDate positionReportDate) {
    return publicHolidays
        .nextWorkingDay(positionReportDate)
        .atTime(fund.getNavCutoffTime())
        .atZone(ESTONIAN_ZONE)
        .toInstant();
  }
}
