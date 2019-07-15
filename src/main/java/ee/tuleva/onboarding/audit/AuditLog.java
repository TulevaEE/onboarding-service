package ee.tuleva.onboarding.audit;

import ee.tuleva.onboarding.config.MapJsonConverter;
import lombok.*;
import org.springframework.boot.actuate.audit.AuditEvent;

import javax.persistence.*;
import java.time.Instant;
import java.util.Map;

@Data
@Builder
@Entity
@Table(name = "audit_log")
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Instant timestamp;

    private String principal;

    private String type;

    @Convert(converter = MapJsonConverter.class)
    private Map<String, Object> data;

    public static AuditLog fromAuditEvent(AuditEvent auditEvent) {
        return AuditLog.builder()
                .timestamp(auditEvent.getTimestamp())
                .principal(auditEvent.getPrincipal())
                .type(auditEvent.getType())
                .data(auditEvent.getData())
                .build();
    }

    public AuditEvent toAuditEvent() {
        return new AuditEvent(
                this.getTimestamp(),
                this.getPrincipal(),
                this.getType(),
                this.getData()
        );
    }

}
