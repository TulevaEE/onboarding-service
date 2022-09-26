package ee.tuleva.onboarding.mandate.processor;

import static ee.tuleva.onboarding.time.ClockHolder.CLOCK;

import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.application.ApplicationType;
import java.time.Instant;
import java.util.Optional;
import javax.persistence.*;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "mandate_process")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MandateProcess {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne
  @JoinColumn(name = "mandate_id", nullable = false)
  private Mandate mandate;

  private String processId;

  @Enumerated(EnumType.STRING)
  private ApplicationType type;

  private Boolean successful;

  private Integer errorCode;

  @NotNull private Instant createdDate;

  @PrePersist
  protected void onCreate() {
    createdDate = CLOCK().instant();
  }

  public Optional<Boolean> isSuccessful() {
    return Optional.ofNullable(successful);
  }

  public Optional<Boolean> getSuccessful() {
    return Optional.ofNullable(successful);
  }

  public Optional<Integer> getErrorCode() {
    return Optional.ofNullable(errorCode);
  }
}
