package ee.tuleva.onboarding.payment;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

@JsonInclude(NON_NULL)
public record PrefilledLink(
    @Nullable String url,
    String recipientName,
    String recipientIban,
    String description,
    @Nullable String amount)
    implements PaymentLink {}
