package ee.tuleva.onboarding.hackathon;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.GenerationType.IDENTITY;
import static org.hibernate.type.SqlTypes.JSON;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.jspecify.annotations.Nullable;

@Data
@Builder
@Entity
@Table(name = "hackathon_registration")
@AllArgsConstructor
@NoArgsConstructor
public class HackathonRegistration {

  @Id
  @GeneratedValue(strategy = IDENTITY)
  private Long id;

  @NotNull
  @Column(nullable = false, updatable = false)
  private Long userId;

  @NotBlank @Email private String email;

  @Nullable private String phoneNumber;

  @Enumerated(STRING)
  @NotNull
  private HackathonRole role;

  @JdbcTypeCode(JSON)
  @NotNull
  private List<HackathonSkill> skills;

  @JdbcTypeCode(JSON)
  @NotNull
  private List<HackathonChallenge> challenges;

  @Enumerated(STRING)
  @NotNull
  private HackathonParticipation participation;

  @Nullable private String idea;

  @Nullable private String linkedinUrl;

  @Column(updatable = false)
  private Instant createdTime;

  private Instant updatedTime;

  public void updateFrom(HackathonRegistrationRequest request, Instant now) {
    email = request.email();
    phoneNumber = request.phoneNumber();
    role = request.role();
    skills = request.skills();
    challenges = request.challenges();
    participation = request.participation();
    idea = request.idea();
    linkedinUrl = request.linkedinUrl();
    updatedTime = now;
  }
}
