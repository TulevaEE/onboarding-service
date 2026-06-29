package ee.tuleva.onboarding.mandate.email.webhook;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Builder;

/**
 * @see <a href="https://mailchimp.com/developer/transactional/docs/webhooks/">Mandrill Webhook
 *     Events</a>
 */
@Builder
public record MandrillWebhookEvent(
    String event,
    @JsonProperty("_id") String id,
    Long ts, // timestamp
    MandrillMessage msg,
    String url) {

  public boolean isOpen() {
    return "open".equals(event);
  }

  public boolean isClick() {
    return "click".equals(event);
  }

  public boolean isEngagementEvent() {
    return isOpen() || isClick();
  }

  public boolean isDeferral() {
    return "deferral".equals(event);
  }

  public boolean isHardBounce() {
    return "hard_bounce".equals(event);
  }

  public boolean isSoftBounce() {
    return "soft_bounce".equals(event);
  }

  public boolean isReject() {
    return "reject".equals(event);
  }

  public boolean isDeliveryFailure() {
    return isDeferral() || isHardBounce() || isSoftBounce() || isReject();
  }

  @Builder
  public record MandrillMessage(
      @JsonProperty("_id") String id,
      String email,
      String subject,
      Object metadata,
      String state,
      String diag,
      @JsonProperty("bounce_description") String bounceDescription,
      @JsonProperty("smtp_events") List<SmtpEvent> smtpEvents) {

    @Builder
    public record SmtpEvent(Long ts, String type, String diag) {}
  }
}
