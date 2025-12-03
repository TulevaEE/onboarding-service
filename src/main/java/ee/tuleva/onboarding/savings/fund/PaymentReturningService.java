package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.RETURNED;

import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.swedbank.http.SwedbankGatewayClient;
import ee.tuleva.onboarding.swedbank.payment.PaymentRequest;
import ee.tuleva.onboarding.user.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentReturningService {

  private final SwedbankGatewayClient swedbankGatewayClient;
  private final SavingFundPaymentRepository savingFundPaymentRepository;
  private final UserRepository userRepository;
  private final SavingsFundLedger savingsFundLedger;

  @Transactional
  public void createReturn(SavingFundPayment payment) {
    sendReturnPaymentOrder(payment);
    savingFundPaymentRepository.changeStatus(payment.getId(), RETURNED);

    if (isUserCancelledPayment(payment)) {
      reserveUserBalanceForReturn(payment);
    }
  }

  private void sendReturnPaymentOrder(SavingFundPayment payment) {
    var returnReason = payment.getReturnReason();
    var description = returnReason != null ? "Tagastus: " + returnReason : "Tagastus";
    var paymentRequest =
        PaymentRequest.tulevaPaymentBuilder(payment.getId())
            .remitterIban(payment.getBeneficiaryIban())
            .beneficiaryName(payment.getRemitterName())
            .beneficiaryIban(payment.getRemitterIban())
            .amount(payment.getAmount())
            .description(description)
            .build();
    swedbankGatewayClient.sendPaymentRequest(paymentRequest, UUID.randomUUID());
  }

  private boolean isUserCancelledPayment(SavingFundPayment payment) {
    return payment.getUserId() != null;
  }

  private void reserveUserBalanceForReturn(SavingFundPayment payment) {
    userRepository
        .findById(payment.getUserId())
        .ifPresent(
            user ->
                savingsFundLedger.reservePaymentForCancellation(
                    user, payment.getAmount(), payment.getId()));
  }
}
