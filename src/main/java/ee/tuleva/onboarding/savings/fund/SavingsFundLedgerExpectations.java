package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.FUND_SUBSCRIPTION;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.PAYMENT_BOUNCE_BACK;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.PAYMENT_CANCELLED;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.REDEMPTION_REQUEST;

import ee.tuleva.onboarding.banking.payment.LedgerExpectations;
import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequestRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class SavingsFundLedgerExpectations implements LedgerExpectations {

  private final SavingsFundLedger savingsFundLedger;
  private final RedemptionRequestRepository redemptionRequestRepository;

  @Override
  public Optional<BigDecimal> pricedRedemption(UUID redemptionRequestId) {
    return savingsFundLedger.cashAmountOf(redemptionRequestId, REDEMPTION_REQUEST);
  }

  @Override
  public Optional<BigDecimal> pricedRedemptionBatch(UUID batchId) {
    return savingsFundLedger.cashAmountOf(
        redemptionRequestRepository.findIdsByBatchId(batchId), REDEMPTION_REQUEST);
  }

  @Override
  public BigDecimal issuedSubscriptions(Instant after, Instant until) {
    return savingsFundLedger.cashAmountBetween(FUND_SUBSCRIPTION, after, until);
  }

  @Override
  public Optional<BigDecimal> bookedReturn(UUID paymentId) {
    return savingsFundLedger
        .cashAmountOf(paymentId, PAYMENT_CANCELLED)
        .or(() -> savingsFundLedger.cashAmountOf(paymentId, PAYMENT_BOUNCE_BACK));
  }
}
