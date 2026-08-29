package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.savings.SavingFundPayment.Status.RESERVED;
import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.savings.SavingFundPayment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentReservationService {

  private final SavingFundPaymentRepository savingFundPaymentRepository;
  private final SavingsFundLedger savingsFundLedger;

  @Transactional
  public void process(SavingFundPayment payment) {
    log.info("Processing reservation for payment {}", payment.getId());

    savingsFundLedger.reservePaymentForSubscription(
        requireNonNull(payment.getPartyId(), "Missing partyId: paymentId=" + payment.getId()),
        payment.getAmount(),
        payment.getId());

    log.info("Reservation completed for payment {}", payment.getId());
    savingFundPaymentRepository.changeStatus(payment.getId(), RESERVED);
  }
}
