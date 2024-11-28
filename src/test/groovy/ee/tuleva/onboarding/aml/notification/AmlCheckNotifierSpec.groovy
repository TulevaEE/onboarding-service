package ee.tuleva.onboarding.aml.notification

import ee.tuleva.onboarding.aml.AmlCheck
import ee.tuleva.onboarding.analytics.thirdpillar.AnalyticsThirdPillar
import ee.tuleva.onboarding.notification.slack.SlackService
import spock.lang.Specification

import static ee.tuleva.onboarding.aml.AmlCheckType.*

class AmlCheckNotifierSpec extends Specification {

  def slackService = Mock(SlackService)
  def amlCheckNotifier = new AmlCheckNotifier(slackService)

  def "sends notifications on some aml check created events when check fails"() {
    given:
    AmlCheck check = AmlCheck.builder()
      .type(checkType)
      .success(false)
      .build()

    when:
    amlCheckNotifier.onAmlCheckCreated(new AmlCheckCreatedEvent(this, check))

    then:
    timesSent * slackService.sendMessage("AML check failed: type=${checkType}")

    where:
    checkType                           | timesSent
    SANCTION                           | 1
    POLITICALLY_EXPOSED_PERSON_AUTO    | 1
    SANCTION_OVERRIDE                  | 0
    POLITICALLY_EXPOSED_PERSON_OVERRIDE| 0
    CONTACT_DETAILS                    | 0
  }

  def "does not send notifications when aml check succeeds"() {
    given:
    AmlCheck check = AmlCheck.builder()
      .type(SANCTION)
      .success(true)
      .build()

    when:
    amlCheckNotifier.onAmlCheckCreated(new AmlCheckCreatedEvent(this, check))

    then:
    0 * slackService.sendMessage(_)
  }

  def "sends notification when aml checks job runs"() {
    given:
    def records = [Mock(AnalyticsThirdPillar), Mock(AnalyticsThirdPillar)]

    when:
    amlCheckNotifier.onScheduledAmlCheckJobRun(new AmlChecksRunEvent(this, records))

    then:
    1 * slackService.sendMessage("Running AML checks job: numberOfRecords=2")
  }
}
