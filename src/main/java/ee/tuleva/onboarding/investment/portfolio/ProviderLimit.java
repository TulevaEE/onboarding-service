package ee.tuleva.onboarding.investment.portfolio;

import static jakarta.persistence.EnumType.STRING;

import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@Entity
@Table(name = "investment_provider_limit")
@AllArgsConstructor
@NoArgsConstructor
public class ProviderLimit {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull private LocalDate effectiveDate;

  @NotNull private String fundCode;

  @Enumerated(STRING)
  @NotNull
  private Provider provider;

  @NotNull private BigDecimal softLimitPercent;

  @NotNull private BigDecimal hardLimitPercent;

  private Instant createdAt;

  @PrePersist
  protected void onCreate() {
    createdAt = Instant.now();
  }
}
