package ee.tuleva.onboarding.notification.email;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record EmailDeliveryFailedEvent(
    String recipient,
    @Nullable String subject,
    String eventType,
    @Nullable String reason,
    String mandrillMessageId,
    Instant timestamp,
    List<String> tags) {

  public static final String TRADE_ORDER_TAG = "trade-order";

  public EmailDeliveryFailedEvent {
    tags = List.copyOf(tags);
  }

  public boolean isTradeOrder() {
    return tags.contains(TRADE_ORDER_TAG);
  }
}
