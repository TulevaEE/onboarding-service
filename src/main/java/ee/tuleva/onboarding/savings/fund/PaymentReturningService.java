package ee.tuleva.onboarding.savings.fund;

import ee.tuleva.onboarding.swedbank.http.SwedbankGatewayClient;
import ee.tuleva.onboarding.swedbank.payment.PaymentRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentReturningService {

  private final SwedbankGatewayClient swedbankGatewayClient;
  private final SavingFundPaymentRepository savingFundPaymentRepository;

  @Transactional
  public void createReturn(SavingFundPayment payment) {
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
    savingFundPaymentRepository.changeStatus(payment.getId(), SavingFundPayment.Status.RETURNED);
  }
}
