package ee.tuleva.onboarding.notification.email;

import static ee.tuleva.onboarding.time.ClockHolder.clock;
import static jakarta.persistence.EnumType.STRING;
import static java.time.temporal.ChronoUnit.DAYS;

import ee.tuleva.onboarding.personalcode.ValidPersonalCode;
import jakarta.persistence.*;
import jakarta.persistence.Column;
import jakarta.validation.constraints.NotNull;
import java.time.Clock;
import java.time.Instant;
import lombok.*;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "email")
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
@ToString(exclude = "personalCode")
public class Email {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ValidPersonalCode private String personalCode;

  private @Nullable String mandrillMessageId;

  private @Nullable String mailchimpCampaign;

  @NotNull
  @Enumerated(STRING)
  private EmailType type;

  @NotNull
  @Enumerated(STRING)
  private EmailStatus status;

  @Column(name = "mandate_id")
  private @Nullable Long mandateId;

  @Column(name = "mandate_batch_id")
  private @Nullable Long mandateBatchId;

  @NotNull private Instant createdDate;

  @NotNull private Instant updatedDate;

  @PrePersist
  protected void onCreate() {
    createdDate = clock().instant();
    updatedDate = clock().instant();
  }

  @PreUpdate
  protected void onUpdate() {
    updatedDate = clock().instant();
  }

  public boolean isToday(Clock clock) {
    return Instant.now(clock).truncatedTo(DAYS).equals(createdDate.truncatedTo(DAYS));
  }
}
