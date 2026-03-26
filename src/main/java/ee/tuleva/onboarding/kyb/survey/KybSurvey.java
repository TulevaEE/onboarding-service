package ee.tuleva.onboarding.kyb.survey;

import static org.hibernate.generator.EventType.INSERT;
import static org.hibernate.type.SqlTypes.JSON;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(name = "kyb_survey")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
class KybSurvey {

  @Id @GeneratedValue private UUID id;

  @NotNull
  @Column(name = "user_id")
  private Long userId;

  @NotNull
  @Column(name = "registry_code")
  private String registryCode;

  @JdbcTypeCode(JSON)
  @NotNull
  private KybSurveyResponse survey;

  @Column(name = "created_time", nullable = false, updatable = false, insertable = false)
  @Generated(event = INSERT)
  private Instant createdTime;
}
