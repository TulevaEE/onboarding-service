package ee.tuleva.onboarding.payment;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PrefilledLink(
    String url, String recipientName, String recipientIban, String description, String amount)
    implements PaymentLink {}
