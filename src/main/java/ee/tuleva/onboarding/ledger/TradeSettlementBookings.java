package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.TRADE_SETTLEMENT;
import static ee.tuleva.onboarding.ledger.SavingsFundLedger.MetadataKey.BROKER_REFERENCE;
import static ee.tuleva.onboarding.ledger.SavingsFundLedger.MetadataKey.INSTRUMENT;
import static ee.tuleva.onboarding.ledger.SystemAccount.TRADE_CASH_SETTLEMENT;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TradeSettlementBookings {

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");

  private final LedgerTransactionRepository transactionRepository;

  @Transactional(readOnly = true)
  public List<TradeSettlementBooking> bookedSince(LocalDate since) {
    return transactionRepository
        .findAllByTransactionTypeAndTransactionDateGreaterThanEqual(
            TRADE_SETTLEMENT, since.atStartOfDay(ESTONIAN_ZONE).toInstant())
        .stream()
        .map(TradeSettlementBookings::booking)
        .toList();
  }

  private static TradeSettlementBooking booking(LedgerTransaction transaction) {
    return new TradeSettlementBooking(
        fund(transaction),
        transaction.getExternalReference(),
        (String) transaction.getMetadata().get(BROKER_REFERENCE.getKey()),
        (String) transaction.getMetadata().get(INSTRUMENT.getKey()),
        transaction.getTransactionDate().atZone(ESTONIAN_ZONE).toLocalDate(),
        cashAmount(transaction));
  }

  private static BigDecimal cashAmount(LedgerTransaction transaction) {
    return transaction.getEntries().stream()
        .filter(TradeSettlementBookings::isTradeCash)
        .map(LedgerEntry::getAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add)
        .negate();
  }

  private static @Nullable TulevaFund fund(LedgerTransaction transaction) {
    return transaction.getEntries().stream()
        .filter(TradeSettlementBookings::isTradeCash)
        .map(entry -> entry.getAccount().getName())
        .filter(Objects::nonNull)
        .map(accountName -> accountName.split(":"))
        .filter(segments -> segments.length > 1)
        .map(segments -> TulevaFund.valueOf(segments[1]))
        .findFirst()
        .orElse(null);
  }

  private static boolean isTradeCash(LedgerEntry entry) {
    String accountName = entry.getAccount().getName();
    return accountName != null && accountName.startsWith(TRADE_CASH_SETTLEMENT.name() + ":");
  }
}
