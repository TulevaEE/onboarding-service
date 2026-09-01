package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.POSITION_UPDATE;
import static ee.tuleva.onboarding.ledger.SystemAccount.*;
import static java.nio.charset.StandardCharsets.UTF_8;

import ee.tuleva.onboarding.ledger.LedgerTransactionService.LedgerEntryDto;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NavPositionLedger {

  private final LedgerAccountService ledgerAccountService;
  private final LedgerTransactionService ledgerTransactionService;
  private final JdbcClient jdbcClient;

  @Transactional
  public void recordPositions(
      TulevaFund fund,
      LocalDate reportDate,
      Instant transactionAt,
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

    List<LedgerEntryDto> entries =
        buildPositionEntries(fund, securitiesUnits, cashValue, receivablesValue, payablesValue);
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
        transactionAt,
        externalReference,
        metadata,
        entries.toArray(new LedgerEntryDto[0]));
  }

  private List<LedgerEntryDto> buildPositionEntries(
      TulevaFund fund,
      Map<String, BigDecimal> securitiesUnits,
      BigDecimal cashValue,
      BigDecimal receivablesValue,
      BigDecimal payablesValue) {

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

    return entries;
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

  @Transactional
  public int deletePositionUpdatesForDates(TulevaFund fund, List<LocalDate> dates) {
    if (dates.isEmpty()) {
      return 0;
    }

    List<UUID> refs = dates.stream().map(date -> generatePositionReference(fund, date)).toList();

    int deleted =
        jdbcClient
            .sql(
                """
                DELETE FROM ledger.entry
                WHERE transaction_id IN (
                  SELECT id FROM ledger.transaction
                  WHERE external_reference IN (:refs)
                )
                """)
            .param("refs", refs)
            .update();

    int txDeleted =
        jdbcClient
            .sql(
                """
                DELETE FROM ledger.transaction
                WHERE external_reference IN (:refs)
                """)
            .param("refs", refs)
            .update();

    String fundName = fund.name();
    for (LocalDate date : dates) {
      deleted +=
          jdbcClient
              .sql(
                  """
                  DELETE FROM ledger.entry
                  WHERE transaction_id IN (
                    SELECT id FROM ledger.transaction
                    WHERE transaction_type = 'POSITION_UPDATE'
                      AND CAST(metadata AS VARCHAR) LIKE :fundPattern
                      AND CAST(metadata AS VARCHAR) LIKE :correctionPattern
                      AND CAST(metadata AS VARCHAR) LIKE :datePattern
                  )
                  """)
              .param("fundPattern", "%\"fund\":%\"" + fundName + "\"%")
              .param("correctionPattern", "%POSITION_CORRECTION%")
              .param("datePattern", "%\"reportDate\":%\"" + date + "\"%")
              .update();

      txDeleted +=
          jdbcClient
              .sql(
                  """
                  DELETE FROM ledger.transaction
                  WHERE transaction_type = 'POSITION_UPDATE'
                    AND CAST(metadata AS VARCHAR) LIKE :fundPattern
                    AND CAST(metadata AS VARCHAR) LIKE :correctionPattern
                    AND CAST(metadata AS VARCHAR) LIKE :datePattern
                  """)
              .param("fundPattern", "%\"fund\":%\"" + fundName + "\"%")
              .param("correctionPattern", "%POSITION_CORRECTION%")
              .param("datePattern", "%\"reportDate\":%\"" + date + "\"%")
              .update();
    }

    log.info(
        "Deleted position updates for dates: fund={}, dates={}, transactions={}, entries={}",
        fund,
        dates.size(),
        txDeleted,
        deleted);
    return txDeleted;
  }

  @Transactional
  public int deletePositionUpdatesByFund(TulevaFund fund) {
    String fundName = fund.name();
    int deleted =
        jdbcClient
            .sql(
                """
                DELETE FROM ledger.entry
                WHERE transaction_id IN (
                  SELECT id FROM ledger.transaction
                  WHERE transaction_type = 'POSITION_UPDATE'
                    AND CAST(metadata AS VARCHAR) LIKE :fundPattern
                )
                """)
            .param("fundPattern", "%\"fund\":%\"" + fundName + "\"%")
            .update();

    int txDeleted =
        jdbcClient
            .sql(
                """
                DELETE FROM ledger.transaction
                WHERE transaction_type = 'POSITION_UPDATE'
                  AND CAST(metadata AS VARCHAR) LIKE :fundPattern
                """)
            .param("fundPattern", "%\"fund\":%\"" + fundName + "\"%")
            .update();

    log.info(
        "Deleted position updates: fund={}, transactions={}, entries={}", fund, txDeleted, deleted);
    return txDeleted;
  }

  private UUID generatePositionReference(TulevaFund fund, LocalDate reportDate) {
    String key = "POSITION_UPDATE:" + fund.name() + ":" + reportDate;
    return UUID.nameUUIDFromBytes(key.getBytes(UTF_8));
  }

  private LedgerEntryDto entry(LedgerAccount account, BigDecimal amount) {
    return new LedgerEntryDto(account, amount);
  }
}
