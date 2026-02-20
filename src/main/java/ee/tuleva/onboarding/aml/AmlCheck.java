package ee.tuleva.onboarding.aml;

import static org.hibernate.type.SqlTypes.JSON;

import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.user.personalcode.ValidPersonalCode;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.springframework.data.annotation.CreatedDate;

@Entity
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
@EqualsAndHashCode
public class AmlCheck {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ValidPersonalCode private String personalCode;

  @Enumerated(value = EnumType.STRING)
  @NotNull
  private AmlCheckType type;

  private boolean success;

  @NotNull
  @Builder.Default
  @JdbcTypeCode(JSON)
  private Map<String, Object> metadata = new HashMap<>();

  @CreatedDate private Instant createdTime;

  @PrePersist
  protected void onCreate() {
    createdTime = ClockHolder.clock().instant();
  }
}
