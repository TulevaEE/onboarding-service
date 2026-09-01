package ee.tuleva.onboarding.payment.provider.montonio;

import static java.util.Objects.requireNonNull;

import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class MontonioPaymentChannel {
  @Nullable String accessKey;
  @Nullable String secretKey;
  @Nullable String bic;

  public String getAccessKey() {
    return requireNonNull(accessKey, "Missing Montonio payment channel accessKey");
  }

  public String getSecretKey() {
    return requireNonNull(secretKey, "Missing Montonio payment channel secretKey");
  }

  public @Nullable String getBic() {
    return bic;
  }
}
