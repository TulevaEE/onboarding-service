package ee.tuleva.onboarding.mandate.email.persistence;

import static ee.tuleva.onboarding.time.ClockHolder.clock;
import static jakarta.persistence.EnumType.STRING;

import ee.tuleva.onboarding.mandate.Mandate;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
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

  @NotNull private Long userId;
  @NotNull private String mandrillMessageId;

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

  @PrePersist
  protected void onCreate() {
    createdDate = clock().instant();
  }
}
