package ee.tuleva.onboarding.kyb;

import static org.hibernate.generator.EventType.INSERT;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;

@Entity
@Table(name = "kyb_check_override")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KybCheckOverride {

  @Id @GeneratedValue private UUID id;

  @NotNull
  @Column(name = "registry_code")
  private String registryCode;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(name = "check_type")
  private KybCheckType checkType;

  @Column(name = "forced_success")
  private boolean forcedSuccess;

  @NotNull private String reason;

  @NotNull
  @Column(name = "created_by")
  private String createdBy;

  @Column(name = "created_time", nullable = false, updatable = false, insertable = false)
  @Generated(event = INSERT)
  private Instant createdTime;
}
