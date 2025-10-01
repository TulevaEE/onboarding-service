package ee.tuleva.onboarding.swedbank.payment;

import java.math.BigDecimal;
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
    String endToEndId) {}
