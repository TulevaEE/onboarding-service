package ee.tuleva.onboarding.payment.provider.montonio;

import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.COOP;
import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.LHV;
import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.SWEDBANK;
import static ee.tuleva.onboarding.payment.PaymentFixture.aMontonioPaymentChannel;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.payment.PaymentData.PaymentChannel;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MontonioPaymentChannelConfigurationTest {

  @Test
  void banksSharingAStoreVerifyTokensWithThatStoresSecretKey() {
    var configuration =
        configurationOf(
            Map.of(
                SWEDBANK, aMontonioPaymentChannel("sharedStore", "sharedSecret", "HABAEE2X"),
                COOP, aMontonioPaymentChannel("sharedStore", "sharedSecret", "EKRDEE22"),
                LHV, aMontonioPaymentChannel("lhvStore", "lhvSecret", "LHVBEE22")));

    assertThat(configuration.getSecretKey("sharedStore")).isEqualTo("sharedSecret");
    assertThat(configuration.getSecretKey("lhvStore")).isEqualTo("lhvSecret");
  }

  @Test
  void anUnknownAccessKeyHasNoSecretKey() {
    var configuration =
        configurationOf(Map.of(LHV, aMontonioPaymentChannel("lhvStore", "lhvSecret", "LHVBEE22")));

    assertThat(configuration.getSecretKey("someOtherStore")).isNull();
  }

  @Test
  void banksSharingAnAccessKeyMustShareItsSecretKey() {
    var configuration =
        new MontonioPaymentChannelConfiguration(
            Map.of(
                SWEDBANK, aMontonioPaymentChannel("sharedStore", "oneSecret", "HABAEE2X"),
                COOP, aMontonioPaymentChannel("sharedStore", "anotherSecret", "EKRDEE22")));

    assertThatThrownBy(configuration::mapSecretKeysByAccessKey)
        .isInstanceOf(IllegalStateException.class);
  }

  private static MontonioPaymentChannelConfiguration configurationOf(
      Map<PaymentChannel, MontonioPaymentChannel> channels) {
    var configuration = new MontonioPaymentChannelConfiguration(channels);
    configuration.mapSecretKeysByAccessKey();
    return configuration;
  }
}
