package ee.tuleva.onboarding.investment.fees;

import static ee.tuleva.onboarding.investment.fees.FeeType.*;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.FEE_ACCRUAL;
import static ee.tuleva.onboarding.ledger.SystemAccount.*;
import static java.math.RoundingMode.HALF_UP;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import ee.tuleva.onboarding.comparisons.fundvalue.ResolvedPrice;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.ledger.FeeMonthSettlement;
import ee.tuleva.onboarding.ledger.LedgerEntryAmount;
import ee.tuleva.onboarding.ledger.NavFeeAccrualLedger;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import ee.tuleva.onboarding.ledger.SystemAccount;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
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
  private final OutstandingFees outstandingFees;
  private final NavFeeAccrualLedger navFeeAccrualLedger;
  private final NavLedgerRepository navLedgerRepository;
  private final FeeMonthResolver feeMonthResolver;
  private final FeeChargedToFundPolicy feeChargedToFundPolicy;
  private final PublicHolidays publicHolidays;

  private void releaseAtMonthEndIfNeeded(TulevaFund fund, LocalDate month) {
    Instant cutoff = startOfDay(month.plusMonths(1));
    LocalDate settlementDate = month.plusMonths(1).minusDays(1);

    for (FeeType feeType : FeeType.values()) {
      if (!outstandingFees.releasedAtMonthEnd(feeType, month)) {
        continue;
      }
      SystemAccount feeAccount = feeType.getAccrualAccount();
      BigDecimal balance =
          navLedgerRepository.getSystemAccountBalanceBefore(
              feeAccount.getAccountName(fund), cutoff);
      BigDecimal settlementAmount = balance.negate();
      if (settlementAmount.signum() != 0) {
        navFeeAccrualLedger.settleFeeAccrual(
            fund, month, settlementDate, feeAccount, settlementAmount, 0);
      }
    }
  }

  private void settleWhatTheDebitsCleared(
      TulevaFund fund,
      FeeType feeType,
      List<FeeMonthBalance> months,
      LocalDate positionReportDate) {
    if (outstandingFees.releasedAtMonthEnd(feeType, positionReportDate.withDayOfMonth(1))) {
      return;
    }
    SystemAccount feeAccount = feeType.getAccrualAccount();
    releaseWhatIsLeftFromBeforeTheFirstHeldMonth(fund, feeAccount);
    months.forEach(month -> settleToWhatTheDebitsPaid(fund, feeAccount, month, positionReportDate));
  }

  private void releaseWhatIsLeftFromBeforeTheFirstHeldMonth(
      TulevaFund fund, SystemAccount feeAccount) {
    LocalDate firstHeldMonth = outstandingFees.firstFeeMonthSettledByPayment();
    BigDecimal left =
        navLedgerRepository.getSystemAccountBalanceBefore(
            feeAccount.getAccountName(fund), startOfDay(firstHeldMonth));
    if (left.signum() == 0) {
      return;
    }
    LocalDate lastReleasedMonth = firstHeldMonth.minusMonths(1);
    navFeeAccrualLedger.settleFeeAccrual(
        fund,
        lastReleasedMonth,
        firstHeldMonth.minusDays(1),
        feeAccount,
        left.negate(),
        navFeeAccrualLedger.settlementOf(fund, lastReleasedMonth, feeAccount).revisions().size());
  }

  private void settleToWhatTheDebitsPaid(
      TulevaFund fund,
      SystemAccount feeAccount,
      FeeMonthBalance month,
      LocalDate positionReportDate) {
    FeeMonthSettlement settlement =
        navFeeAccrualLedger.settlementOf(fund, month.feeMonth(), feeAccount);
    int revision = settlement.revisions().size();
    BigDecimal postedNow = BigDecimal.ZERO;
    for (SettlementTarget target :
        settlementTargets(fund, feeAccount, month, settlement, positionReportDate)) {
      BigDecimal delta =
          target.amount().subtract(settlement.settledThrough(target.date())).subtract(postedNow);
      if (delta.signum() != 0) {
        navFeeAccrualLedger.settleFeeAccrual(
            fund, month.feeMonth(), target.date(), feeAccount, delta, revision++);
        postedNow = postedNow.add(delta);
      }
    }
  }

  private List<SettlementTarget> settlementTargets(
      TulevaFund fund,
      SystemAccount feeAccount,
      FeeMonthBalance month,
      FeeMonthSettlement settlement,
      LocalDate positionReportDate) {
    TreeMap<LocalDate, BigDecimal> owedAfterEachDebitDay =
        month.payments().stream()
            .collect(
                toMap(
                    FeeMonthPayment::bookedOn,
                    FeeMonthPayment::owedAfter,
                    (earlier, later) -> later,
                    TreeMap::new));
    BigDecimal accrued =
        month.hasBeenPaid()
            ? navFeeAccrualLedger.accruedIn(fund, month.feeMonth(), feeAccount)
            : BigDecimal.ZERO;
    TreeSet<LocalDate> dates = new TreeSet<>(owedAfterEachDebitDay.keySet());
    settlement.revisions().stream()
        .map(FeeMonthSettlement.Revision::settledOn)
        .filter(settledOn -> !settledOn.isAfter(positionReportDate))
        .forEach(dates::add);
    dates.add(positionReportDate);
    return dates.stream()
        .map(
            date -> {
              Map.Entry<LocalDate, BigDecimal> owedAfter = owedAfterEachDebitDay.floorEntry(date);
              return new SettlementTarget(
                  date,
                  owedAfter == null ? BigDecimal.ZERO : accrued.subtract(owedAfter.getValue()));
            })
        .toList();
  }

  private record SettlementTarget(LocalDate date, BigDecimal amount) {}

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
    metadata.put("feeMonth", accrual.feeMonth());
    metadata.put("dailyAmountGross", accrual.dailyAmountGross());
    metadata.put("ledgerAmount", ledgerAmount);
    return metadata;
  }

  @Transactional
  public FeeResult calculateFeesForNav(
      TulevaFund fund,
      LocalDate positionReportDate,
      BigDecimal baseValue,
      Map<String, ResolvedPrice> securityPrices) {
    LocalDate startDate =
        feeAccrualRepository
            .findLatestAccrualDate(fund)
            .map(d -> d.plusDays(1))
            .filter(d -> d.isBefore(positionReportDate))
            .orElse(positionReportDate);

    BigDecimal previousBaseValue =
        feeAccrualRepository.findLatestBaseValue(fund, MANAGEMENT).orElse(baseValue);

    log.info(
        "calculateFeesForNav: fund={}, positionReportDate={}, startDate={}",
        fund,
        positionReportDate,
        startDate);

    Map<FeeType, FeeChargedToFundPolicy.Resolver> chargedPolicies =
        Arrays.stream(FeeType.values())
            .collect(
                toMap(identity(), feeType -> feeChargedToFundPolicy.resolverFor(fund, feeType)));

    LocalDate previousFeeMonth = null;
    for (LocalDate day = startDate; !day.isAfter(positionReportDate); day = day.plusDays(1)) {
      LocalDate feeMonth = feeMonthResolver.resolveFeeMonth(day);
      if (!feeMonth.equals(previousFeeMonth)) {
        releaseAtMonthEndIfNeeded(fund, feeMonth.minusMonths(1));
      }
      BigDecimal dayBaseValue = day.isBefore(positionReportDate) ? previousBaseValue : baseValue;
      reconcileDay(fund, day, dayBaseValue, chargedPolicies, securityPrices);
      previousFeeMonth = feeMonth;
    }
    reconcileNonWorkingDaysAfter(
        fund, positionReportDate, baseValue, chargedPolicies, securityPrices);

    List<FeeMonthBalance> managementMonths =
        outstandingFees.monthsOn(policyFor(chargedPolicies, MANAGEMENT), positionReportDate);
    List<FeeMonthBalance> depotMonths =
        outstandingFees.monthsOn(policyFor(chargedPolicies, DEPOT), positionReportDate);
    settleWhatTheDebitsCleared(fund, MANAGEMENT, managementMonths, positionReportDate);
    settleWhatTheDebitsCleared(fund, DEPOT, depotMonths, positionReportDate);
    return new FeeResult(OutstandingFees.owed(managementMonths), OutstandingFees.owed(depotMonths));
  }

  private static FeeChargedToFundPolicy.Resolver policyFor(
      Map<FeeType, FeeChargedToFundPolicy.Resolver> policies, FeeType feeType) {
    return requireNonNull(policies.get(feeType), "No fee policy resolver: feeType=" + feeType);
  }

  private void reconcileNonWorkingDaysAfter(
      TulevaFund fund,
      LocalDate positionReportDate,
      BigDecimal baseValue,
      Map<FeeType, FeeChargedToFundPolicy.Resolver> chargedPolicies,
      Map<String, ResolvedPrice> securityPrices) {
    LocalDate firstDayAfter = positionReportDate.plusDays(1);
    LocalDate nextWorkingDay = publicHolidays.nextWorkingDay(positionReportDate);
    if (!nextWorkingDay.isAfter(firstDayAfter)) {
      return;
    }
    feeAccrualRepository
        .findByFundAndDateRange(fund, firstDayAfter, nextWorkingDay.minusDays(1))
        .stream()
        .map(FeeAccrual::accrualDate)
        .distinct()
        .sorted()
        .forEach(day -> reconcileDay(fund, day, baseValue, chargedPolicies, securityPrices));
  }

  private void reconcileDay(
      TulevaFund fund,
      LocalDate date,
      BigDecimal baseValue,
      Map<FeeType, FeeChargedToFundPolicy.Resolver> chargedPolicies,
      Map<String, ResolvedPrice> securityPrices) {
    for (FeeCalculator calculator : feeCalculators) {
      FeeAccrual accrual = calculator.calculate(fund, date, baseValue);
      Optional<FeeAccrual> existing =
          feeAccrualRepository.findByFundAndAccrualDateAndFeeType(fund, date, accrual.feeType());
      if (existing.filter(accrual::sameValuesAs).isEmpty()) {
        feeAccrualRepository.save(accrual);
      }
      FeeChargedToFundPolicy.Resolver resolver = policyFor(chargedPolicies, accrual.feeType());
      reconcileLedger(fund, date, accrual, resolver.chargedOn(date), securityPrices);
    }
  }

  private void reconcileLedger(
      TulevaFund fund,
      LocalDate date,
      FeeAccrual accrual,
      boolean chargedToFund,
      Map<String, ResolvedPrice> securityPrices) {
    SystemAccount feeAccount = accrual.feeType().getAccrualAccount();
    BigDecimal target =
        chargedToFund ? roundForLedger(accrual.dailyAmountGross()) : BigDecimal.ZERO;
    List<LedgerEntryAmount> entries =
        navLedgerRepository.findEntriesByTransactionTypeBetween(
            feeAccount.getAccountName(fund),
            FEE_ACCRUAL,
            startOfDay(date),
            startOfDay(date.plusDays(1)));
    BigDecimal recorded =
        entries.stream()
            .map(LedgerEntryAmount::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .negate();
    if (target.compareTo(recorded) == 0) {
      log.info(
          "Fee accrual ledger in sync: fund={}, date={}, feeType={}, amount={}",
          fund,
          date,
          accrual.feeType(),
          target);
      return;
    }
    Map<String, Object> metadata = buildAccrualMetadata(accrual, feeAccount, target);
    if (securityPrices != null && !securityPrices.isEmpty()) {
      metadata.put("securityPrices", formatSecurityPrices(securityPrices));
    }
    if (entries.isEmpty()) {
      log.info(
          "Recording fee accrual: fund={}, date={}, feeType={}, ledgerAmount={}",
          fund,
          date,
          accrual.feeType(),
          target);
      navFeeAccrualLedger.recordFeeAccrual(fund, date, feeAccount, target, metadata);
      return;
    }
    BigDecimal delta = target.subtract(recorded);
    int revision = (int) entries.stream().map(LedgerEntryAmount::transactionId).distinct().count();
    metadata.put("operationType", "FEE_ACCRUAL_REVISION");
    metadata.put("previousLedgerAmount", recorded);
    metadata.put("delta", delta);
    metadata.put("revision", revision);
    log.info(
        "Revising fee accrual: fund={}, date={}, feeType={}, revision={}, previous={}, target={}, delta={}",
        fund,
        date,
        accrual.feeType(),
        revision,
        recorded,
        target,
        delta);
    navFeeAccrualLedger.reviseFeeAccrual(fund, date, feeAccount, delta, revision, metadata);
  }

  private static Instant startOfDay(LocalDate date) {
    return date.atStartOfDay(ESTONIAN_ZONE).toInstant();
  }

  private Map<String, String> formatSecurityPrices(Map<String, ResolvedPrice> securityPrices) {
    return securityPrices.entrySet().stream()
        .collect(toMap(Map.Entry::getKey, e -> e.getValue().usedPrice().toPlainString()));
  }
}
