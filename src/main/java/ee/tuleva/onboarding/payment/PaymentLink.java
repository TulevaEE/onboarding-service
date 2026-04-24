package ee.tuleva.onboarding.payment;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentLink(
    String url, String recipientName, String recipientIban, String description, String amount) {

  public PaymentLink(String url) {
    this(url, null, null, null, null);
  }
}
