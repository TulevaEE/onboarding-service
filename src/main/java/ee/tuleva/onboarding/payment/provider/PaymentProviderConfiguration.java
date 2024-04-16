package ee.tuleva.onboarding.payment.provider;

import static java.util.stream.Collectors.toMap;

import ee.tuleva.onboarding.payment.PaymentData.PaymentChannel;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.Map.Entry;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@ConfigurationProperties("payment-provider")
class PaymentProviderConfiguration {

  @Getter private final Map<PaymentChannel, PaymentProviderChannel> paymentChannels;
  private Map<String, PaymentProviderChannel> paymentChannelsByAccessKey;

  public PaymentProviderChannel getPaymentProviderChannel(PaymentChannel paymentChannel) {
    return paymentChannels.get(paymentChannel);
  }

  public PaymentProviderChannel getPaymentProviderChannel(String accessKey) {
    return paymentChannelsByAccessKey.get(accessKey);
  }

  @PostConstruct
  private void mapByAccessKey() {
    paymentChannelsByAccessKey =
        paymentChannels.entrySet().stream()
            .collect(toMap(entry -> entry.getValue().accessKey, Entry::getValue));
  }
}
