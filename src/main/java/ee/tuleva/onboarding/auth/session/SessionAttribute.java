package ee.tuleva.onboarding.auth.session;

import static ee.tuleva.onboarding.time.ClockHolder.clock;

import java.io.Serializable;
import java.time.Instant;
import javax.persistence.*;
import javax.validation.constraints.NotNull;
import lombok.*;

@Entity
@Table(name = "session_attribute")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionAttribute implements Serializable {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull private Long userId;

  @NotNull private String attributeName;

  @NotNull private byte[] attributeValue;

  @NotNull private Instant createdDate;

  @PrePersist
  protected void onCreate() {
    createdDate = clock().instant();
  }
}
