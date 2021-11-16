package ee.tuleva.onboarding.mandate.email.scheduledEmail;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.NotBlank;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Entity
@Table(name = "scheduled_email")
@NoArgsConstructor
@RequiredArgsConstructor
public class ScheduledEmail {
  @NotBlank @NonNull Long userId;
  @NotBlank @NonNull String mandrillMessageId;
  @NotBlank @NonNull ScheduledEmailType type;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
}
