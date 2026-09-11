package ee.tuleva.onboarding.payment.savings;

import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.payment.provider.montonio.MontonioPaymentChannel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties("payment-provider.savings-channel")
@RequiredArgsConstructor
@Data
@EqualsAndHashCode(callSuper = true)
public class SavingsChannelConfiguration extends MontonioPaymentChannel {
  private @Nullable String returnUrl;
  private @Nullable String notificationUrl;

  public String getReturnUrl() {
    return requireNonNull(returnUrl, "Missing payment-provider.savings-channel.return-url");
  }

  public String getNotificationUrl() {
    return requireNonNull(
        notificationUrl, "Missing payment-provider.savings-channel.notification-url");
  }
}
