package ee.tuleva.onboarding.payment.provider.montonio;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import ee.tuleva.onboarding.payment.PaymentData.PaymentChannel;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@ConfigurationProperties("payment-provider")
public class MontonioPaymentChannelConfiguration {

  @Getter private final Map<PaymentChannel, MontonioPaymentChannel> paymentChannels;
  private Map<String, String> secretKeysByAccessKey;

  public @Nullable MontonioPaymentChannel getPaymentProviderChannel(PaymentChannel paymentChannel) {
    return paymentChannels.get(paymentChannel);
  }

  public @Nullable String getSecretKey(String accessKey) {
    return secretKeysByAccessKey.get(accessKey);
  }

  @PostConstruct
  void mapSecretKeysByAccessKey() {
    secretKeysByAccessKey =
        paymentChannels.values().stream()
            .collect(
                groupingBy(
                    MontonioPaymentChannel::getAccessKey,
                    mapping(MontonioPaymentChannel::getSecretKey, toSet())))
            .entrySet()
            .stream()
            .collect(toMap(Entry::getKey, MontonioPaymentChannelConfiguration::theStoresSecretKey));
  }

  private static String theStoresSecretKey(Entry<String, Set<String>> secretKeysByStore) {
    var secretKeys = secretKeysByStore.getValue();
    if (secretKeys.size() != 1) {
      throw new IllegalStateException(
          "Montonio payment channels share an access key but not its secret key: accessKey="
              + secretKeysByStore.getKey());
    }
    return secretKeys.iterator().next();
  }
}
