package ee.tuleva.onboarding.payment.provider.montonio;

import static ee.tuleva.onboarding.currency.Currency.EUR;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.payment.provider.montonio.MontonioOrder.MontonioBillingAddress;
import ee.tuleva.onboarding.payment.provider.montonio.MontonioOrder.MontonioPaymentMethod;
import ee.tuleva.onboarding.payment.provider.montonio.MontonioOrder.MontonioPaymentMethod.MontonioPaymentMethodOptions;
import java.math.BigDecimal;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

class MontonioOrderSerializationTest {

  private final JsonMapper mapper = JsonMapper.builder().build();

  @Test
  void anOrderWithNobodyToBillCarriesNoBillingAddressKeyAtAll() {
    assertThat(asMap(anOrder(null))).doesNotContainKey("billingAddress");
  }

  @Test
  void anOrderWithSomebodyToBillStillCarriesTheBillingAddress() {
    var payer = MontonioBillingAddress.builder().firstName("Jordan").lastName("Valdma").build();

    assertThat(asMap(anOrder(payer)))
        .containsEntry("billingAddress", Map.of("firstName", "Jordan", "lastName", "Valdma"));
  }

  private Map<String, Object> asMap(MontonioOrder order) {
    return mapper.readValue(mapper.writeValueAsString(order), new TypeReference<>() {});
  }

  private static MontonioOrder anOrder(@Nullable MontonioBillingAddress billingAddress) {
    return MontonioOrder.builder()
        .accessKey("testAccessKey")
        .merchantReference("testMerchantReference")
        .returnUrl("http://return.url")
        .notificationUrl("http://notification.url")
        .grandTotal(new BigDecimal("100.00"))
        .currency(EUR)
        .exp(1577873400L)
        .locale("en")
        .payment(
            MontonioPaymentMethod.builder()
                .amount(new BigDecimal("100.00"))
                .currency(EUR)
                .methodOptions(
                    MontonioPaymentMethodOptions.builder()
                        .preferredProvider("testProvider")
                        .preferredLocale("en")
                        .paymentDescription("38812121215, 1577873400")
                        .build())
                .build())
        .billingAddress(billingAddress)
        .build();
  }
}
