package ee.tuleva.onboarding.aml.notification

import ee.tuleva.onboarding.aml.AmlCheck
import ee.tuleva.onboarding.notification.slack.SlackService
import spock.lang.Specification

import static ee.tuleva.onboarding.aml.AmlCheckType.*

class AmlCheckNotifierSpec extends Specification {

  def slackService = Mock(SlackService)
  def amlCheckNotifier = new AmlCheckNotifier(slackService)

  def "sends notifications on some aml check created events"() {
    given:
    AmlCheck check = AmlCheck.builder().type(checkType).build()
    when:
    amlCheckNotifier.onAmlCheckCreated(new AmlCheckCreatedEvent(this, check))
    then:
    timesSent * slackService.sendMessage(_ as String)

    where:
    checkType                           | timesSent
    SANCTION                            | 1
    POLITICALLY_EXPOSED_PERSON_AUTO     | 1
    SANCTION_OVERRIDE                   | 0
    POLITICALLY_EXPOSED_PERSON_OVERRIDE | 0
    CONTACT_DETAILS                     | 0
  }
}
