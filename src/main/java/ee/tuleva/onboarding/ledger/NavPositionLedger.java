package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.SystemAccount.*;

import ee.tuleva.onboarding.ledger.LedgerTransactionService.LedgerEntryDto;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NavPositionLedger {

  private final LedgerAccountService ledgerAccountService;
  private final LedgerTransactionService ledgerTransactionService;
  private final Clock clock;

  @Transactional
  public void recordPositions(
      String fund,
      LocalDate reportDate,
      BigDecimal securitiesValue,
      BigDecimal cashValue,
      BigDecimal receivablesValue,
      BigDecimal payablesValue) {

    List<LedgerEntryDto> entries = new ArrayList<>();

    if (securitiesValue.signum() != 0) {
      entries.add(entry(getSecuritiesAccount(), securitiesValue));
      entries.add(entry(getNavEquityAccount(), securitiesValue.negate()));
    }

    if (cashValue.signum() != 0) {
      entries.add(entry(getCashAccount(), cashValue));
      entries.add(entry(getNavEquityAccount(), cashValue.negate()));
    }

    if (receivablesValue.signum() != 0) {
      entries.add(entry(getReceivablesAccount(), receivablesValue));
      entries.add(entry(getNavEquityAccount(), receivablesValue.negate()));
    }

    if (payablesValue.signum() != 0) {
      entries.add(entry(getNavEquityAccount(), payablesValue.abs()));
      entries.add(entry(getPayablesAccount(), payablesValue.abs().negate()));
    }

    if (entries.isEmpty()) {
      return;
    }

    Map<String, Object> metadata =
        Map.of(
            "operationType", "POSITION_UPDATE", "fund", fund, "reportDate", reportDate.toString());

    ledgerTransactionService.createTransaction(
        Instant.now(clock), metadata, entries.toArray(new LedgerEntryDto[0]));
  }

  private LedgerAccount getSecuritiesAccount() {
    return getSystemAccount(SECURITIES_VALUE);
  }

  private LedgerAccount getCashAccount() {
    return getSystemAccount(CASH_POSITION);
  }

  private LedgerAccount getReceivablesAccount() {
    return getSystemAccount(TRADE_RECEIVABLES);
  }

  private LedgerAccount getPayablesAccount() {
    return getSystemAccount(TRADE_PAYABLES);
  }

  private LedgerAccount getNavEquityAccount() {
    return getSystemAccount(NAV_EQUITY);
  }

  private LedgerAccount getSystemAccount(SystemAccount systemAccount) {
    return ledgerAccountService
        .findSystemAccount(systemAccount)
        .orElseGet(() -> ledgerAccountService.createSystemAccount(systemAccount));
  }

  private LedgerEntryDto entry(LedgerAccount account, BigDecimal amount) {
    return new LedgerEntryDto(account, amount);
  }
}
