package ee.tuleva.onboarding.payment.provider.montonio;

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
public class MontonioPaymentChannelConfiguration {

  @Getter private final Map<PaymentChannel, MontonioPaymentChannel> paymentChannels;
  private Map<String, MontonioPaymentChannel> paymentChannelsByAccessKey;

  public MontonioPaymentChannel getPaymentProviderChannel(PaymentChannel paymentChannel) {
    return paymentChannels.get(paymentChannel);
  }

  public MontonioPaymentChannel getPaymentProviderChannel(String accessKey) {
    return paymentChannelsByAccessKey.get(accessKey);
  }

  @PostConstruct
  private void mapByAccessKey() {
    paymentChannelsByAccessKey =
        paymentChannels.entrySet().stream()
            .collect(toMap(entry -> entry.getValue().accessKey, Entry::getValue));
  }
}
