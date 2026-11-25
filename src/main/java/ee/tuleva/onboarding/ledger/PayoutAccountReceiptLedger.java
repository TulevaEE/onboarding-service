package ee.tuleva.onboarding.ledger;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PayoutAccountReceiptLedger {

  private final UnattributedPaymentLedgerRecorder unattributedRecorder;

  public LedgerTransaction recordReceipt(
      BigDecimal amount, UUID externalReference, LocalDate bookingDate) {
    return unattributedRecorder.recordUnattributedPayoutAccountReceipt(
        amount, externalReference, bookingDate);
  }

  public LedgerTransaction recordReturn(
      BigDecimal amount, UUID externalReference, LocalDate bookingDate) {
    return unattributedRecorder.bounceBackUnattributedPayoutAccountReceipt(
        amount, externalReference, bookingDate);
  }
}
