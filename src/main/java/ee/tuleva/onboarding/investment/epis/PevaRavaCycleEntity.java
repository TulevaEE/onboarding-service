package ee.tuleva.onboarding.investment.epis;

import static ee.tuleva.onboarding.investment.report.ReportType.R17_PEVA;
import static ee.tuleva.onboarding.investment.report.ReportType.R21_RAVA;
import static ee.tuleva.onboarding.time.ClockHolder.clock;
import static jakarta.persistence.EnumType.STRING;

import ee.tuleva.onboarding.investment.report.ReportType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

@Data
@Builder
@Entity
@Table(name = "peva_rava_cycle")
@AllArgsConstructor
@NoArgsConstructor
public class PevaRavaCycleEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull private LocalDate lockDate;

  @NotNull private LocalDate execDate;

  @NotNull
  @Enumerated(STRING)
  @Builder.Default
  private PevaRavaPhase phase = PevaRavaPhase.IGNORE;

  @Nullable
  @Column(name = "r17_report_id")
  private Long r17ReportId;

  @Nullable
  @Column(name = "r21_report_id")
  private Long r21ReportId;

  private Instant createdAt;

  private Instant updatedAt;

  public List<ReportType> missingReports() {
    List<ReportType> missing = new ArrayList<>();
    if (r17ReportId == null) {
      missing.add(R17_PEVA);
    }
    if (r21ReportId == null) {
      missing.add(R21_RAVA);
    }
    return List.copyOf(missing);
  }

  static PevaRavaCycleEntity forCycle(PevaRavaCycle cycle) {
    return PevaRavaCycleEntity.builder()
        .lockDate(cycle.lockDate())
        .execDate(cycle.execDate())
        .build();
  }

  @PrePersist
  protected void onCreate() {
    Instant now = clock().instant();
    if (createdAt == null) {
      createdAt = now;
    }
    if (updatedAt == null) {
      updatedAt = now;
    }
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = clock().instant();
  }
}
