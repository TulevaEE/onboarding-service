package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.UNIT_COUNT_UPDATE;
import static ee.tuleva.onboarding.ledger.SystemAccount.FUND_UNITS_EQUITY;
import static ee.tuleva.onboarding.ledger.SystemAccount.FUND_UNITS_OUTSTANDING;
import static java.nio.charset.StandardCharsets.UTF_8;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.ledger.LedgerTransactionService.LedgerEntryDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class EpisUnitCountLedgerRecorder {

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");

  private final LedgerAccountService ledgerAccountService;
  private final LedgerTransactionService ledgerTransactionService;

  @Transactional
  public void recordUnitCount(TulevaFund fund, LocalDate date, BigDecimal totalUnits) {
    UUID externalReference = generateReference(fund, date);
    if (ledgerTransactionService.existsByExternalReferenceAndTransactionType(
        externalReference, UNIT_COUNT_UPDATE)) {
      log.debug("Unit count already recorded: fund={}, date={}", fund, date);
      return;
    }

    Instant transactionDate =
        date.atTime(fund.getNavCutoffTime()).plusMinutes(1).atZone(ESTONIAN_ZONE).toInstant();

    LedgerAccount unitsAccount = getSystemAccount(FUND_UNITS_OUTSTANDING, fund);
    BigDecimal currentBalance = unitsAccount.getBalanceAt(transactionDate);
    BigDecimal delta = totalUnits.subtract(currentBalance);

    if (delta.signum() == 0) {
      log.debug("Unit count unchanged: fund={}, date={}, units={}", fund, date, totalUnits);
      return;
    }
    Map<String, Object> metadata =
        Map.of(
            "operationType", "UNIT_COUNT_UPDATE",
            "fund", fund.name(),
            "date", date.toString(),
            "totalUnits", totalUnits.toPlainString());

    ledgerTransactionService.createTransaction(
        UNIT_COUNT_UPDATE,
        transactionDate,
        externalReference,
        metadata,
        new LedgerEntryDto(unitsAccount, delta),
        new LedgerEntryDto(getSystemAccount(FUND_UNITS_EQUITY, fund), delta.negate()));

    log.info(
        "Recorded unit count update: fund={}, date={}, totalUnits={}, delta={}",
        fund,
        date,
        totalUnits,
        delta);
  }

  private UUID generateReference(TulevaFund fund, LocalDate date) {
    String key = "UNIT_COUNT:" + fund.name() + ":" + date;
    return UUID.nameUUIDFromBytes(key.getBytes(UTF_8));
  }

  private LedgerAccount getSystemAccount(SystemAccount systemAccount, TulevaFund fund) {
    return ledgerAccountService
        .findSystemAccount(systemAccount, fund)
        .orElseGet(() -> ledgerAccountService.createSystemAccount(systemAccount, fund));
  }
}
