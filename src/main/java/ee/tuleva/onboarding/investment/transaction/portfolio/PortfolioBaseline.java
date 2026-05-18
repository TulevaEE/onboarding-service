package ee.tuleva.onboarding.investment.transaction.portfolio;

import static ee.tuleva.onboarding.time.ClockHolder.clock;
import static jakarta.persistence.CascadeType.ALL;
import static jakarta.persistence.FetchType.EAGER;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
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

@Data
@Builder
@Entity
@Table(name = "investment_portfolio_baseline")
@AllArgsConstructor
@NoArgsConstructor
public class PortfolioBaseline {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull private String fundIsin;

  @NotNull private LocalDate baselineDate;

  @Column(name = "loaded_at")
  private Instant loadedAt;

  private String loadedBy;

  @OneToMany(cascade = ALL, orphanRemoval = true, fetch = EAGER)
  @JoinColumn(name = "baseline_id", nullable = false)
  @Builder.Default
  private List<PortfolioBaselineEntry> entries = new ArrayList<>();

  @PrePersist
  protected void onCreate() {
    if (loadedAt == null) {
      loadedAt = clock().instant();
    }
  }

  public void addEntry(PortfolioBaselineEntry entry) {
    if (entries == null) {
      entries = new ArrayList<>();
    }
    entries.add(entry);
  }
}
