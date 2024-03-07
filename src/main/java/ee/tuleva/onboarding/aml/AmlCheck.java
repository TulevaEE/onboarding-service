package ee.tuleva.onboarding.aml;

import ee.tuleva.onboarding.user.personalcode.ValidPersonalCode;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import javax.persistence.*;
import javax.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.Type;
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
  @Type(type = "jsonb")
  @Column(columnDefinition = "jsonb")
  @Convert(disableConversion = true)
  private Map<String, Object> metadata = new HashMap<>();

  @CreatedDate private Instant createdTime;

  @PrePersist
  protected void onCreate() {
    createdTime = Instant.now();
  }
}
