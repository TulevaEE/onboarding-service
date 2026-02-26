package ee.tuleva.onboarding.payment.provider.montonio;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import ee.tuleva.onboarding.currency.Currency;
import ee.tuleva.onboarding.payment.provider.PaymentReference;
import ee.tuleva.onboarding.payment.provider.PaymentReferenceDeserializer;
import java.math.BigDecimal;
import lombok.Data;
import org.springframework.lang.Nullable;
import tools.jackson.databind.annotation.JsonDeserialize;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MontonioOrderToken {
  private String uuid;
  private String accessKey;

  @JsonDeserialize(using = PaymentReferenceDeserializer.class)
  private PaymentReference merchantReference;

  private String merchantReferenceDisplay;
  private MontonioOrderStatus paymentStatus;
  private String paymentMethod;
  private BigDecimal grandTotal;
  private Currency currency;
  @Nullable private String senderIban;
  @Nullable private String senderName;
  @Nullable private String paymentProviderName;
  private Long iat;
  private Long exp;

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
