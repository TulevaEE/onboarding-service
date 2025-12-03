package ee.tuleva.onboarding.swedbank.payment;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.Builder;

@Builder
public record PaymentRequest(
    String remitterName,
    String remitterId,
    String remitterIban,
    String remitterBic,
    String beneficiaryName,
    String beneficiaryIban,
    BigDecimal amount,
    String description,
    String ourId,
    String endToEndId) {

  public static PaymentRequestBuilder tulevaPaymentBuilder(UUID fromId) {
    var id = fromId.toString().replace("-", "");
    return PaymentRequest.builder()
        .remitterName("Tuleva Fondid AS")
        .remitterId("14118923")
        .remitterBic("HABAEE2X")
        .ourId(id)
        .endToEndId(id);
  }
}
