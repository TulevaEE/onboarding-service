package ee.tuleva.onboarding.investment.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OrderSenderTest {

  @ParameterizedTest
  @CsvSource({
    "fundmanager@tuleva.ee, true",
    "FundManager@Tuleva.EE, true",
    "@tuleva.ee, false",
    "fundmanager@tuleva.ee.example, false",
    "fundmanager@example.com, false"
  })
  void sendsOnlyFromAnAddressOfItsOwnAtTuleva_whateverTheCase(String email, boolean tuleva) {
    assertThat(new OrderSender(email, "Fund Manager").hasTulevaAddress()).isEqualTo(tuleva);
  }
}
