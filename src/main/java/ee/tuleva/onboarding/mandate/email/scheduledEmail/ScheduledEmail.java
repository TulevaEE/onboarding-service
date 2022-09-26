package ee.tuleva.onboarding.mandate.email.scheduledEmail;

import static ee.tuleva.onboarding.time.ClockHolder.clock;
import static javax.persistence.EnumType.STRING;

import java.time.Instant;
import javax.persistence.Entity;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Entity
@Table(name = "scheduled_email")
@NoArgsConstructor
@RequiredArgsConstructor
@Data
public class ScheduledEmail {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NonNull private Long userId;
  @NonNull private String mandrillMessageId;

  @Enumerated(STRING)
  @NonNull
  private ScheduledEmailType type;

  @NotNull private Instant createdDate;

  @PrePersist
  protected void onCreate() {
    createdDate = clock().instant();
  }
}
