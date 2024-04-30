package ee.tuleva.onboarding.mandate.email.persistence;

import static ee.tuleva.onboarding.time.ClockHolder.clock;
import static jakarta.persistence.EnumType.STRING;
import static java.time.temporal.ChronoUnit.DAYS;

import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.user.personalcode.ValidPersonalCode;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.Clock;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "email")
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
public class Email {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ValidPersonalCode private String personalCode;

  private String mandrillMessageId;

  @NotNull
  @Enumerated(STRING)
  private EmailType type;

  @NotNull
  @Enumerated(STRING)
  private EmailStatus status;

  @ManyToOne
  @JoinColumn(name = "mandate_id")
  private Mandate mandate;

  @NotNull private Instant createdDate;

  @NotNull private Instant updatedDate;

  @PrePersist
  protected void onCreate() {
    createdDate = clock().instant();
    updatedDate = Instant.now();
  }

  @PreUpdate
  protected void onUpdate() {
    updatedDate = Instant.now();
  }

  public boolean isToday(Clock clock) {
    return Instant.now(clock).truncatedTo(DAYS).equals(createdDate.truncatedTo(DAYS));
  }
}
