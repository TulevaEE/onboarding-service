package ee.tuleva.onboarding.notification.email;

import java.time.Instant;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record EmailDeliveryFailedEvent(
    String recipient,
    @Nullable String subject,
    String eventType,
    @Nullable String reason,
    String mandrillMessageId,
    Instant timestamp) {}
