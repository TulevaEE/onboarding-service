package ee.tuleva.onboarding.savings.fund;

import ee.tuleva.onboarding.payment.IncomingSavingsPayment;
import ee.tuleva.onboarding.payment.SavingsPayments;
import ee.tuleva.onboarding.savings.SavingFundPayment;
import ee.tuleva.onboarding.savings.SavingFundPaymentQueries;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class IncomingSavingsPaymentRecorder implements SavingsPayments {

  private final SavingFundPaymentQueries savingFundPaymentQueries;

  @Override
  public boolean recordIncoming(IncomingSavingsPayment incomingPayment) {
    if (!savingFundPaymentQueries.findRecentPayments(incomingPayment.description()).isEmpty()) {
      log.info("Saving fund payment already exists: description={}", incomingPayment.description());
      return false;
    }

    var payment =
        SavingFundPayment.builder()
            .remitterName(incomingPayment.remitterName())
            .remitterIban(incomingPayment.remitterIban())
            .description(incomingPayment.description())
            .amount(incomingPayment.amount())
            .currency(incomingPayment.currency())
            .build();

    var paymentId = savingFundPaymentQueries.savePaymentData(payment);
    savingFundPaymentQueries.attachParty(paymentId, incomingPayment.recipient());

    return true;
  }
}
