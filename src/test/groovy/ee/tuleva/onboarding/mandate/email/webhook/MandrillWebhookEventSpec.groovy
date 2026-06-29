package ee.tuleva.onboarding.mandate.email.webhook

import spock.lang.Specification
import spock.lang.Unroll

class MandrillWebhookEventSpec extends Specification {

  @Unroll
  def "isDeliveryFailure is #expected for event #event"() {
    expect:
    MandrillWebhookEvent.builder().event(event).build().isDeliveryFailure() == expected

    where:
    event         || expected
    "deferral"    || true
    "hard_bounce" || true
    "soft_bounce" || true
    "reject"      || true
    "open"        || false
    "click"       || false
    "send"        || false
    null          || false
  }

  @Unroll
  def "isEngagementEvent stays open/click only: #event is #expected"() {
    expect:
    MandrillWebhookEvent.builder().event(event).build().isEngagementEvent() == expected

    where:
    event         || expected
    "open"        || true
    "click"       || true
    "deferral"    || false
    "hard_bounce" || false
  }
}
