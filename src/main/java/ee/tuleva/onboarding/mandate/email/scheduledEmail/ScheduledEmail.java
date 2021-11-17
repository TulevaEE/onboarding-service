package ee.tuleva.onboarding.mandate.email.scheduledEmail;

import static javax.persistence.EnumType.STRING;

import javax.persistence.Entity;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Entity
@Table(name = "scheduled_email")
@NoArgsConstructor
@RequiredArgsConstructor
@Getter
public class ScheduledEmail {
  @NonNull private Long userId;
  @NonNull private String mandrillMessageId;

  @Enumerated(STRING)
  @NonNull
  private ScheduledEmailType type;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
}
