package ee.tuleva.onboarding.investment.fees;

import static ee.tuleva.onboarding.investment.fees.FeeType.*;
import static ee.tuleva.onboarding.ledger.SystemAccount.*;
import static java.math.RoundingMode.HALF_UP;
import static java.util.stream.Collectors.toMap;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.calculation.ResolvedPrice;
import ee.tuleva.onboarding.ledger.NavFeeAccrualLedger;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import ee.tuleva.onboarding.ledger.SystemAccount;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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

  private static final Map<FeeType, SystemAccount> FEE_TYPE_ACCOUNTS =
      Map.of(
          MANAGEMENT, MANAGEMENT_FEE_ACCRUAL,
          DEPOT, DEPOT_FEE_ACCRUAL);

  private final List<FeeCalculator> feeCalculators;
  private final FeeAccrualRepository feeAccrualRepository;
  private final NavFeeAccrualLedger navFeeAccrualLedger;
  private final NavLedgerRepository navLedgerRepository;
  private final FeeMonthResolver feeMonthResolver;

  private void settleMonthlyFeesIfNeeded(TulevaFund fund, LocalDate month) {
    Instant cutoff = month.plusMonths(1).atStartOfDay().atZone(ESTONIAN_ZONE).toInstant();
    LocalDate settlementDate = month.plusMonths(1).minusDays(1);

    FEE_TYPE_ACCOUNTS.forEach(
        (feeType, feeAccount) -> {
          BigDecimal balance =
              navLedgerRepository.getSystemAccountBalanceBefore(
                  feeAccount.getAccountName(fund), cutoff);
          BigDecimal settlementAmount = balance.negate();
          if (settlementAmount.signum() > 0) {
            navFeeAccrualLedger.settleFeeAccrual(
                fund, settlementDate, feeAccount, settlementAmount);
          }
        });
  }

  private BigDecimal roundForLedger(BigDecimal amount) {
    return amount != null ? amount.setScale(2, HALF_UP) : null;
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
    metadata.put("dailyAmountNet", accrual.dailyAmountNet());
    if (accrual.vatRate() != null) {
      metadata.put("vatRate", accrual.vatRate());
      metadata.put("dailyAmountGross", accrual.dailyAmountGross());
    }
    metadata.put("ledgerAmount", ledgerAmount);
    return metadata;
  }

  @Transactional
  public FeeResult calculateFeesForNav(
      TulevaFund fund,
      LocalDate positionReportDate,
      BigDecimal baseValue,
      Instant feeCutoff,
      Map<String, ResolvedPrice> securityPrices) {
    LocalDate startDate =
        feeAccrualRepository
            .findLatestAccrualDate(fund)
            .map(d -> d.plusDays(1))
            .orElse(positionReportDate);

    LocalDate previousFeeMonth = null;
    for (LocalDate day = startDate; !day.isAfter(positionReportDate); day = day.plusDays(1)) {
      LocalDate feeMonth = feeMonthResolver.resolveFeeMonth(day);
      if (!feeMonth.equals(previousFeeMonth)) {
        settleMonthlyFeesIfNeeded(fund, feeMonth.minusMonths(1));
      }
      recordDailyFees(fund, day, baseValue, securityPrices);
      previousFeeMonth = feeMonth;
    }

    BigDecimal mgmtFee =
        navLedgerRepository
            .getSystemAccountBalanceBefore(MANAGEMENT_FEE_ACCRUAL.getAccountName(fund), feeCutoff)
            .negate();
    BigDecimal depotFee =
        navLedgerRepository
            .getSystemAccountBalanceBefore(DEPOT_FEE_ACCRUAL.getAccountName(fund), feeCutoff)
            .negate();
    return new FeeResult(mgmtFee, depotFee);
  }

  private void recordDailyFees(
      TulevaFund fund,
      LocalDate date,
      BigDecimal baseValue,
      Map<String, ResolvedPrice> securityPrices) {
    for (FeeCalculator calculator : feeCalculators) {
      FeeAccrual accrual = calculator.calculate(fund, date, baseValue);
      feeAccrualRepository.save(accrual);
      SystemAccount feeAccount = FEE_TYPE_ACCOUNTS.get(accrual.feeType());
      BigDecimal ledgerAmount = roundForLedger(accrual.dailyAmountNet());
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
