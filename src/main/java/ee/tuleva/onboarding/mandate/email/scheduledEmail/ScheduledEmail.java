package ee.tuleva.onboarding.mandate.email.scheduledEmail;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "scheduled_email")
@AllArgsConstructor
@NoArgsConstructor
public class ScheduledEmail {
  @NotBlank Long userId;
  @NotBlank String mandrillMessageId;
  @NotBlank ScheduledEmailType type;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  enum ScheduledEmailType {
    SUGGEST_SECOND_PILLAR
  }
}
