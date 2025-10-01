package ee.tuleva.onboarding.payment.provider;

import static ee.tuleva.onboarding.payment.PaymentData.*;

import java.util.Locale;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@AllArgsConstructor
@RequiredArgsConstructor
public class PaymentReference {

  private String personalCode;

  private String recipientPersonalCode;

  private UUID uuid;

  private PaymentType paymentType;

  private Locale locale;

  private String description;
}
