package ee.tuleva.onboarding.payment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class PaymentLinkJacksonTest {

  private final ObjectMapper objectMapper = JsonMapper.builder().build();

  @Test
  void redirectLinkRoundTripsViaTypeDiscriminator() throws Exception {
    PaymentLink original = new RedirectLink("https://x");

    var json = objectMapper.writeValueAsString(original);
    PaymentLink decoded = objectMapper.readValue(json, PaymentLink.class);

    assertThat(decoded).isInstanceOf(RedirectLink.class).isEqualTo(original);
    assertThat(objectMapper.readTree(json).get("type").asString()).isEqualTo("REDIRECT");
  }

  @Test
  void prefilledLinkRoundTripsViaTypeDiscriminator() throws Exception {
    PaymentLink original =
        new PrefilledLink(
            "https://lhv.example",
            "AS Pensionikeskus",
            "EE547700771002908125",
            "30101119828",
            "50");

    var json = objectMapper.writeValueAsString(original);
    PaymentLink decoded = objectMapper.readValue(json, PaymentLink.class);

    assertThat(decoded).isInstanceOf(PrefilledLink.class).isEqualTo(original);
    assertThat(objectMapper.readTree(json).get("type").asString()).isEqualTo("PREFILLED");
  }

  @Test
  void prefilledLinkOmitsNullUrl() throws Exception {
    PaymentLink link =
        new PrefilledLink(
            null, "Tuleva Täiendav Kogumisfond", "EE711010220306707220", "38812121215", "50");

    JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(link));

    assertThat(json.has("url")).isFalse();
    assertThat(json.get("recipientIban").asString()).isEqualTo("EE711010220306707220");
  }
}
