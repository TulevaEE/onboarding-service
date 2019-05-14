package ee.tuleva.onboarding.audit


import org.springframework.boot.actuate.audit.listener.AuditApplicationEvent
import org.springframework.context.ApplicationEventPublisher
import spock.lang.Specification

class AuditEventPublisherSpec extends Specification {

    ApplicationEventPublisher applicationEventPublisher = Mock(ApplicationEventPublisher)
    AuditEventPublisher auditEventPublisher = new AuditEventPublisher(applicationEventPublisher)

    def "Publish"() {
        given:
        String samplePrincipal = "principal"
        when:
        auditEventPublisher.publish(samplePrincipal, AuditEventType.LOGIN)
        then:
        1 * applicationEventPublisher.publishEvent({ AuditApplicationEvent auditApplicationEvent ->
            auditApplicationEvent.auditEvent.principal == samplePrincipal &&
                auditApplicationEvent.auditEvent.type == String.valueOf(AuditEventType.LOGIN)
        })
    }

    def "Publish with data"() {
        given:
        String samplePrincipal = "principal"
        when:
        auditEventPublisher.publish(samplePrincipal, AuditEventType.LOGIN, "some=data")
        then:
        1 * applicationEventPublisher.publishEvent({ AuditApplicationEvent auditApplicationEvent ->
            auditApplicationEvent.auditEvent.principal == samplePrincipal &&
                auditApplicationEvent.auditEvent.type == String.valueOf(AuditEventType.LOGIN) &&
                auditApplicationEvent.auditEvent.data.get("some") == "data"
        })
    }
}
