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
@Table(name = "hackathon_idea")
@AllArgsConstructor
@NoArgsConstructor
public class HackathonIdea {

  @Id
  @GeneratedValue(strategy = IDENTITY)
  private Long id;

  @NotNull
  @Column(nullable = false, updatable = false)
  private Long userId;

  @Enumerated(STRING)
  @NotNull
  private HackathonChallenge challenge;

  @NotBlank private String problem;

  @NotBlank private String solution;

  @Nullable private String progress;

  @JdbcTypeCode(JSON)
  @NotNull
  private List<HackathonSkill> neededSkills;

  @Nullable private String additionalInfo;

  @Column(updatable = false)
  private Instant createdTime;
}
