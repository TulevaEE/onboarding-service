package ee.tuleva.onboarding.payment.savings;

import ee.tuleva.onboarding.payment.provider.montonio.MontonioPaymentChannel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties("payment-provider.savings-channel")
@RequiredArgsConstructor
@Data
@EqualsAndHashCode(callSuper = true)
public class SavingsChannelConfiguration extends MontonioPaymentChannel {
  private String returnUrl;
  private String notificationUrl;
}
