package ee.tuleva.onboarding.aml;

import ee.tuleva.onboarding.config.MapJsonConverter;
import ee.tuleva.onboarding.user.User;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import javax.persistence.*;
import javax.validation.constraints.NotNull;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;

@Entity
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString(exclude = {"user"})
@EqualsAndHashCode
public class AmlCheck {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne private User user;

  @Enumerated(value = EnumType.STRING)
  @NotNull
  private AmlCheckType type;

  private boolean success;

  @NotNull
  @Builder.Default
  @Convert(converter = MapJsonConverter.class)
  private Map<String, Object> metadata = new HashMap<>();

  @CreatedDate private Instant createdTime;

  @PrePersist
  protected void onCreate() {
    createdTime = Instant.now();
  }
}
