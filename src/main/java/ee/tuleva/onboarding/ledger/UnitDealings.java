package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.FUND_SUBSCRIPTION;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.REDEMPTION_REQUEST;

import ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UnitDealings {

  private static final List<TransactionType> UNITS_ISSUED_OR_REDEEMED_AT_A_NAV =
      List.of(FUND_SUBSCRIPTION, REDEMPTION_REQUEST);

  private final LedgerTransactionRepository ledgerTransactionRepository;

  @Transactional(readOnly = true)
  public List<UnitDealing> bookedBetween(Instant from, Instant to) {
    return ledgerTransactionRepository
        .findAllByTransactionTypeInAndTransactionDateGreaterThanEqualAndTransactionDateLessThan(
            UNITS_ISSUED_OR_REDEEMED_AT_A_NAV, from, to)
        .stream()
        .map(LedgerTransaction::toUnitDealing)
        .toList();
  }
}
