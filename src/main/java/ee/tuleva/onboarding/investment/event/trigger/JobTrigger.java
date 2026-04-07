package ee.tuleva.onboarding.investment.event.trigger;

import static ee.tuleva.onboarding.time.ClockHolder.clock;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@Entity
@Table(name = "investment_job_trigger")
@AllArgsConstructor
@NoArgsConstructor
class JobTrigger {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull private String jobName;

  @NotNull @Builder.Default private String status = "PENDING";

  private Instant createdAt;
  private Instant startedAt;
  private Instant completedAt;
  private String errorMessage;

  @PrePersist
  protected void onCreate() {
    if (createdAt == null) {
      createdAt = clock().instant();
    }
  }
}
