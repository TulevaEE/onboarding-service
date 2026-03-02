package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.POSITION_UPDATE;
import static ee.tuleva.onboarding.ledger.SystemAccount.*;
import static java.nio.charset.StandardCharsets.UTF_8;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.ledger.LedgerTransactionService.LedgerEntryDto;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NavPositionLedger {

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");

  private final LedgerAccountService ledgerAccountService;
  private final LedgerTransactionService ledgerTransactionService;
  private final PublicHolidays publicHolidays;
  private final Clock clock;

  @Transactional
  public void recordPositions(
      TulevaFund fund,
      LocalDate reportDate,
      Map<String, BigDecimal> securitiesUnits,
      BigDecimal cashValue,
      BigDecimal receivablesValue,
      BigDecimal payablesValue) {

    UUID externalReference = generatePositionReference(fund, reportDate);
    if (ledgerTransactionService.existsByExternalReferenceAndTransactionType(
        externalReference, POSITION_UPDATE)) {
      log.debug("Position update already recorded: fund={}, reportDate={}", fund, reportDate);
      return;
    }

    List<LedgerEntryDto> entries = new ArrayList<>();

    securitiesUnits.forEach(
        (isin, units) -> {
          if (units.signum() != 0) {
            entries.add(entry(findOrCreateInstrumentAccount(SECURITIES_UNITS, fund, isin), units));
            entries.add(
                entry(
                    findOrCreateInstrumentAccount(SECURITIES_UNITS_EQUITY, fund, isin),
                    units.negate()));
          }
        });

    if (cashValue.signum() != 0) {
      entries.add(entry(getSystemAccount(CASH_POSITION, fund), cashValue));
      entries.add(entry(getSystemAccount(NAV_EQUITY, fund), cashValue.negate()));
    }

    if (receivablesValue.signum() != 0) {
      entries.add(entry(getSystemAccount(TRADE_RECEIVABLES, fund), receivablesValue));
      entries.add(entry(getSystemAccount(NAV_EQUITY, fund), receivablesValue.negate()));
    }

    if (payablesValue.signum() != 0) {
      entries.add(entry(getSystemAccount(TRADE_PAYABLES, fund), payablesValue));
      entries.add(entry(getSystemAccount(NAV_EQUITY, fund), payablesValue.negate()));
    }

    if (entries.isEmpty()) {
      return;
    }

    Map<String, Object> metadata =
        Map.of(
            "operationType",
            "POSITION_UPDATE",
            "fund",
            fund.name(),
            "reportDate",
            reportDate.toString());

    ledgerTransactionService.createTransaction(
        POSITION_UPDATE,
        transactionDate(reportDate),
        externalReference,
        metadata,
        entries.toArray(new LedgerEntryDto[0]));
  }

  private Instant transactionDate(LocalDate reportDate) {
    Instant now = Instant.now(clock);
    LocalDate expectedDate = publicHolidays.nextWorkingDay(reportDate);
    LocalDate nowDate = now.atZone(ESTONIAN_ZONE).toLocalDate();
    if (nowDate.equals(expectedDate)) {
      return now;
    }
    return expectedDate.atTime(10, 0).atZone(ESTONIAN_ZONE).toInstant();
  }

  private LedgerAccount findOrCreateInstrumentAccount(
      SystemAccount systemAccount, TulevaFund fund, String isin) {
    String accountName = systemAccount.getAccountName(fund, isin);
    return ledgerAccountService
        .findSystemAccountByName(
            accountName, systemAccount.getAccountType(), systemAccount.getAssetType())
        .orElseGet(
            () ->
                ledgerAccountService.createSystemAccount(
                    accountName, systemAccount.getAccountType(), systemAccount.getAssetType()));
  }

  private LedgerAccount getSystemAccount(SystemAccount systemAccount, TulevaFund fund) {
    return ledgerAccountService
        .findSystemAccount(systemAccount, fund)
        .orElseGet(() -> ledgerAccountService.createSystemAccount(systemAccount, fund));
  }

  private UUID generatePositionReference(TulevaFund fund, LocalDate reportDate) {
    String key = "POSITION_UPDATE:" + fund.name() + ":" + reportDate;
    return UUID.nameUUIDFromBytes(key.getBytes(UTF_8));
  }

  private LedgerEntryDto entry(LedgerAccount account, BigDecimal amount) {
    return new LedgerEntryDto(account, amount);
  }
}
