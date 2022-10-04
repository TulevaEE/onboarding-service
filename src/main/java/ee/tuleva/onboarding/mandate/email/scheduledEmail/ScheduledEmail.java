package ee.tuleva.onboarding.mandate.email.scheduledEmail;

import static ee.tuleva.onboarding.time.ClockHolder.clock;
import static javax.persistence.EnumType.STRING;

import ee.tuleva.onboarding.mandate.Mandate;
import java.time.Instant;
import javax.persistence.Entity;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

@Entity
@Table(name = "scheduled_email")
@Builder
@AllArgsConstructor
@NoArgsConstructor
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

  @ManyToOne
  @JoinColumn(name = "mandate_id")
  private Mandate mandate;

  @NotNull private Instant createdDate;

  @PrePersist
  protected void onCreate() {
    createdDate = clock().instant();
  }
}
