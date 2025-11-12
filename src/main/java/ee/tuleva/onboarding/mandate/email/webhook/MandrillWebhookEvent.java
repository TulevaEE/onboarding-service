package ee.tuleva.onboarding.mandate.email.webhook;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.Map;
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

  public boolean isSupported() {
    return isOpen() || isClick();
  }

  @Builder
  public record MandrillMessage(
      @JsonProperty("_id") String id, String email, String subject, Map<String, Object> metadata) {
    public MandrillMessage {
      metadata = metadata != null ? metadata : new HashMap<>();
    }
  }
}
