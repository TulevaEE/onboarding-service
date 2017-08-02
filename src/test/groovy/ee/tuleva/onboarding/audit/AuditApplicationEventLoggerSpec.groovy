package ee.tuleva.onboarding.audit

import org.springframework.boot.actuate.audit.listener.AuditApplicationEvent
import org.springframework.boot.actuate.security.AuthenticationAuditListener
import spock.lang.Specification

class AuditApplicationEventLoggerSpec extends Specification {

    AuditLogRepository auditLogRepository = Mock(AuditLogRepository)
    AuditApplicationEventLogger service = new AuditApplicationEventLogger(auditLogRepository)

    def "OnAuditEvent"() {
        given:
        AuditApplicationEvent event = new AuditApplicationEvent(
                "principal", "type", [data: true]
        )

        when:
        service.onAuditEvent(event)
        then:

        1 * auditLogRepository.save({AuditLog auditLog ->
            auditLog.principal == event.auditEvent.principal &&
                    auditLog.type == event.auditEvent.type &&
                    auditLog.data == event.auditEvent.data
        })

    }

    def "OnAuditEvent: Don't log authentication success events"() {
        given:
        AuditApplicationEvent event = new AuditApplicationEvent(
                "principal", AuthenticationAuditListener.AUTHENTICATION_SUCCESS, [data: true]
        )

        when:
        service.onAuditEvent(event)
        then:

        0 * auditLogRepository.save({AuditLog auditLog ->
            auditLog.principal == event.auditEvent.principal &&
                    auditLog.type == event.auditEvent.type &&
                    auditLog.data == event.auditEvent.data
        })

    }
}
