package ee.tuleva.onboarding.payment.provider;

import static ee.tuleva.onboarding.payment.PaymentData.*;

import ee.tuleva.onboarding.party.PartyId;
import java.util.Locale;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
@AllArgsConstructor
public class PaymentReference {

  // Null when the payer was never logged in, which is how a gift link works: the money is
  // identified by who it is *for*, not by who sent it.
  private @Nullable String personalCode;

  private String recipientPersonalCode;

  private UUID uuid;

  private PaymentType paymentType;

  private Locale locale;

  private String description;

  private PartyId.Type recipientPartyType;
}
