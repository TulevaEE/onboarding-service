package ee.tuleva.onboarding.audit

import ee.tuleva.onboarding.mandate.Mandate
import ee.tuleva.onboarding.mandate.event.AfterMandateSignedEvent
import ee.tuleva.onboarding.user.User
import spock.lang.Specification

class MandateSuccessfulAuditEventBroadcasterSpec extends Specification {

  AuditEventPublisher auditEventPublisher = Mock(AuditEventPublisher)
  MandateSuccessfulAuditEventBroadcaster service = new MandateSuccessfulAuditEventBroadcaster(auditEventPublisher)

  def "Broadcast mandate successful event"() {
    given:
    def personalCode = '3762394717'
    User user = Mock({
      getPersonalCode() >> personalCode
    })
    Mandate mandate = Mock({
      getPillar() >> pillar
    })
    def event = new AfterMandateSignedEvent(new Object(), user, mandate, Locale.ENGLISH)

    when:
    service.publishMandateSuccessfulEvent(event)

    then:
    1 * auditEventPublisher.publish(personalCode, AuditEventType.MANDATE_SUCCESSFUL, eventData)

    where:
    pillar | eventData
    2      | "pillar=2"
    3      | "pillar=3"

  }
}
