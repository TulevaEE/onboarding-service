package ee.tuleva.onboarding.investment.fees;

import static ee.tuleva.onboarding.investment.fees.FeeType.*;
import static ee.tuleva.onboarding.ledger.SystemAccount.*;
import static java.math.RoundingMode.HALF_UP;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import ee.tuleva.onboarding.comparisons.fundvalue.ResolvedPrice;
import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.ledger.NavFeeAccrualLedger;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import ee.tuleva.onboarding.ledger.SystemAccount;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeeCalculationService {

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");

  private final List<FeeCalculator> feeCalculators;
  private final FeeAccrualRepository feeAccrualRepository;
  private final NavFeeAccrualLedger navFeeAccrualLedger;
  private final NavLedgerRepository navLedgerRepository;
  private final FeeMonthResolver feeMonthResolver;
  private final FeeChargedToFundPolicy feeChargedToFundPolicy;

  private void settleMonthlyFeesIfNeeded(TulevaFund fund, LocalDate month) {
    Instant cutoff = month.plusMonths(1).atStartOfDay().atZone(ESTONIAN_ZONE).toInstant();
    LocalDate settlementDate = month.plusMonths(1).minusDays(1);

    for (FeeType feeType : FeeType.values()) {
      SystemAccount feeAccount = feeType.getAccrualAccount();
      BigDecimal balance =
          navLedgerRepository.getSystemAccountBalanceBefore(
              feeAccount.getAccountName(fund), cutoff);
      BigDecimal settlementAmount = balance.negate();
      if (settlementAmount.signum() > 0) {
        navFeeAccrualLedger.settleFeeAccrual(fund, settlementDate, feeAccount, settlementAmount);
      }
    }
  }

  private BigDecimal roundForLedger(BigDecimal amount) {
    return amount.setScale(2, HALF_UP);
  }

  private Map<String, Object> buildAccrualMetadata(
      FeeAccrual accrual, SystemAccount feeAccount, BigDecimal ledgerAmount) {
    var metadata = new HashMap<String, Object>();
    metadata.put("operationType", "FEE_ACCRUAL");
    metadata.put("fund", accrual.fund().name());
    metadata.put("feeType", feeAccount.name());
    metadata.put("accrualDate", accrual.accrualDate());
    metadata.put("baseValue", accrual.baseValue());
    metadata.put("annualRate", accrual.annualRate());
    metadata.put("daysInYear", accrual.daysInYear());
    metadata.put("referenceDate", accrual.referenceDate());
    metadata.put("feeMonth", accrual.feeMonth());
    metadata.put("dailyAmountGross", accrual.dailyAmountGross());
    metadata.put("ledgerAmount", ledgerAmount);
    return metadata;
  }

  @Transactional
  public FeeResult calculateFeesForNav(
      TulevaFund fund,
      LocalDate positionReportDate,
      FeeBases bases,
      Instant feeCutoff,
      Map<String, ResolvedPrice> securityPrices) {
    LocalDate startDate =
        feeAccrualRepository
            .findLatestAccrualDate(fund)
            .map(d -> d.plusDays(1))
            .orElse(positionReportDate);

    FeeBases previousBases =
        new FeeBases(
            feeAccrualRepository.findLatestBaseValue(fund, MANAGEMENT).orElse(bases.navFeeBase()),
            feeAccrualRepository.findLatestBaseValue(fund, DEPOT).orElse(bases.assetValue()));

    log.info(
        "calculateFeesForNav: fund={}, positionReportDate={}, startDate={}, willProcess={}",
        fund,
        positionReportDate,
        startDate,
        !startDate.isAfter(positionReportDate));

    Map<FeeType, FeeChargedToFundPolicy.Resolver> chargedPolicies =
        Arrays.stream(FeeType.values())
            .collect(
                toMap(identity(), feeType -> feeChargedToFundPolicy.resolverFor(fund, feeType)));

    LocalDate previousFeeMonth = null;
    for (LocalDate day = startDate; !day.isAfter(positionReportDate); day = day.plusDays(1)) {
      LocalDate feeMonth = feeMonthResolver.resolveFeeMonth(day);
      if (!feeMonth.equals(previousFeeMonth)) {
        settleMonthlyFeesIfNeeded(fund, feeMonth.minusMonths(1));
      }
      FeeBases dayBases = day.isBefore(positionReportDate) ? previousBases : bases;
      recordDailyFees(fund, day, dayBases, chargedPolicies, securityPrices);
      previousFeeMonth = feeMonth;
    }

    BigDecimal mgmtFee =
        feeAccrualRepository.getUnsettledAccrual(fund, MANAGEMENT, positionReportDate);
    BigDecimal depotFee = feeAccrualRepository.getUnsettledAccrual(fund, DEPOT, positionReportDate);
    return new FeeResult(mgmtFee, depotFee);
  }

  private void recordDailyFees(
      TulevaFund fund,
      LocalDate date,
      FeeBases bases,
      Map<FeeType, FeeChargedToFundPolicy.Resolver> chargedPolicies,
      Map<String, ResolvedPrice> securityPrices) {
    for (FeeCalculator calculator : feeCalculators) {
      FeeAccrual accrual = calculator.calculate(fund, date, bases);
      feeAccrualRepository.save(accrual);
      FeeChargedToFundPolicy.Resolver resolver =
          requireNonNull(
              chargedPolicies.get(accrual.feeType()),
              "No fee policy resolver: feeType=" + accrual.feeType());
      if (!resolver.chargedOn(date)) {
        log.info(
            "recordDailyFees: fund={}, date={}, feeType={}, tracked but not charged to the fund",
            fund,
            date,
            accrual.feeType());
        continue;
      }
      SystemAccount feeAccount = accrual.feeType().getAccrualAccount();
      BigDecimal ledgerAmount = roundForLedger(accrual.dailyAmountGross());
      log.info(
          "recordDailyFees: fund={}, date={}, feeType={}, ledgerAmount={}",
          fund,
          date,
          accrual.feeType(),
          ledgerAmount);
      Map<String, Object> metadata = buildAccrualMetadata(accrual, feeAccount, ledgerAmount);
      if (securityPrices != null && !securityPrices.isEmpty()) {
        metadata.put("securityPrices", formatSecurityPrices(securityPrices));
      }
      navFeeAccrualLedger.recordFeeAccrual(fund, date, feeAccount, ledgerAmount, metadata);
    }
  }

  private Map<String, String> formatSecurityPrices(Map<String, ResolvedPrice> securityPrices) {
    return securityPrices.entrySet().stream()
        .collect(toMap(Map.Entry::getKey, e -> e.getValue().usedPrice().toPlainString()));
  }
}
