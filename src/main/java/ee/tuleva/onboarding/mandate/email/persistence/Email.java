package ee.tuleva.onboarding.mandate.email.persistence;

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
