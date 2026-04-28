package ee.tuleva.onboarding.payment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class PaymentLinkJacksonTest {

  private final ObjectMapper objectMapper = JsonMapper.builder().build();

  @Test
  void redirectLink_serializesWithTypeDiscriminator() throws Exception {
    PaymentLink link = new RedirectLink("https://x");

    var json = objectMapper.writeValueAsString(link);

    assertThat(json).isEqualTo("{\"type\":\"REDIRECT\",\"url\":\"https://x\"}");
  }

  @Test
  void redirectLink_deserializesViaTypeDiscriminator() throws Exception {
    var json = "{\"type\":\"REDIRECT\",\"url\":\"https://x\"}";

    PaymentLink link = objectMapper.readValue(json, PaymentLink.class);

    assertThat(link).isInstanceOf(RedirectLink.class);
    assertThat(link.url()).isEqualTo("https://x");
  }

  @Test
  void prefilledLink_serializesAllFieldsWhenPopulated() throws Exception {
    PaymentLink link =
        new PrefilledLink(
            "https://lhv.example",
            "AS Pensionikeskus",
            "EE547700771002908125",
            "30101119828",
            "50");

    var json = objectMapper.writeValueAsString(link);

    assertThat(json)
        .isEqualTo(
            "{\"type\":\"PREFILLED\",\"url\":\"https://lhv.example\","
                + "\"recipientName\":\"AS Pensionikeskus\","
                + "\"recipientIban\":\"EE547700771002908125\","
                + "\"description\":\"30101119828\",\"amount\":\"50\"}");
  }

  @Test
  void prefilledLink_omitsNullUrl() throws Exception {
    PaymentLink link =
        new PrefilledLink(
            null, "Tuleva Täiendav Kogumisfond", "EE711010220306707220", "38812121215", "50");

    var json = objectMapper.writeValueAsString(link);

    assertThat(json).doesNotContain("\"url\"");
    assertThat(json).contains("\"type\":\"PREFILLED\"");
    assertThat(json).contains("\"recipientIban\":\"EE711010220306707220\"");
  }

  @Test
  void prefilledLink_roundTrips() throws Exception {
    PaymentLink original =
        new PrefilledLink(
            "https://lhv.example",
            "AS Pensionikeskus",
            "EE547700771002908125",
            "30101119828",
            "50");

    var json = objectMapper.writeValueAsString(original);
    PaymentLink decoded = objectMapper.readValue(json, PaymentLink.class);

    assertThat(decoded).isEqualTo(original);
  }
}
