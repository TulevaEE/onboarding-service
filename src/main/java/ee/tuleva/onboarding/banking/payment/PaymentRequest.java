package ee.tuleva.onboarding.banking.payment;

import java.math.BigDecimal;
import lombok.Builder;

@Builder
public record PaymentRequest(
    String remitterName,
    String remitterId,
    String remitterIban,
    String beneficiaryName,
    String beneficiaryIban,
    BigDecimal amount,
    String description,
    String ourId,
    String endToEndId) {

  public static PaymentRequestBuilder tulevaPaymentBuilder(String id) {
    return PaymentRequest.builder()
        .remitterName("Tuleva Täiendav Kogumisfond")
        .remitterId("1162")
        .ourId(id)
        .endToEndId(id);
  }
}
