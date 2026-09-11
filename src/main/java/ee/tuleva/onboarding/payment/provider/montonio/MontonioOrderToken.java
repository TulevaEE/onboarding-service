package ee.tuleva.onboarding.payment.provider.montonio;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import ee.tuleva.onboarding.currency.Currency;
import ee.tuleva.onboarding.payment.provider.PaymentReference;
import ee.tuleva.onboarding.payment.provider.PaymentReferenceDeserializer;
import java.math.BigDecimal;
import lombok.Data;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.annotation.JsonDeserialize;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MontonioOrderToken {
  @Nullable private String uuid;
  @Nullable private String accessKey;

  @JsonDeserialize(using = PaymentReferenceDeserializer.class)
  @Nullable
  private PaymentReference merchantReference;

  @Nullable private String merchantReferenceDisplay;
  @Nullable private MontonioOrderStatus paymentStatus;
  @Nullable private String paymentMethod;
  @Nullable private BigDecimal grandTotal;
  @Nullable private Currency currency;
  @Nullable private String senderIban;
  @Nullable private String senderName;
  @Nullable private String paymentProviderName;
  @Nullable private Long iat;
  @Nullable private Long exp;

  public enum MontonioOrderStatus {
    PENDING,
    PAID,
    VOIDED,
    PARTIALLY_REFUNDED,
    REFUNDED,
    ABANDONED,
    AUTHORIZED,
  }
}
