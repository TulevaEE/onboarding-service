package ee.tuleva.onboarding.payment.provider;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;

public class PaymentReferenceDeserializer extends JsonDeserializer<PaymentReference> {
  @Override
  public PaymentReference deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
    String rawJson = p.getValueAsString();
    if (rawJson == null || rawJson.isEmpty()) return null;
    ObjectMapper mapper = (ObjectMapper) p.getCodec();
    return mapper.readValue(rawJson, PaymentReference.class);
  }
}
