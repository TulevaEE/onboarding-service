package ee.tuleva.onboarding.payment.provider;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

public class PaymentReferenceDeserializer extends ValueDeserializer<PaymentReference> {
  @Override
  public PaymentReference deserialize(JsonParser p, DeserializationContext ctxt) {
    String rawJson = p.getValueAsString();
    if (rawJson == null || rawJson.isEmpty()) return null;
    return ctxt.readValue(ctxt.createParser(rawJson), PaymentReference.class);
  }
}
