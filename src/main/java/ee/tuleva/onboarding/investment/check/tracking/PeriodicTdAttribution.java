package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.time.ClockHolder.clock;
import static jakarta.persistence.CascadeType.ALL;
import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.FetchType.EAGER;
import static org.hibernate.type.SqlTypes.JSON;

import ee.tuleva.onboarding.fund.TulevaFund;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;

@Data
@Builder
@Entity
@Table(name = "investment_td_attribution")
@AllArgsConstructor
@NoArgsConstructor
class PeriodicTdAttribution {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull
  @Enumerated(STRING)
  @Column(name = "fund_code")
  private TulevaFund fund;

  @NotNull private LocalDate periodStart;
  @NotNull private LocalDate periodEnd;

  @NotNull
  @Enumerated(STRING)
  private PeriodType periodType;

  @NotNull private BigDecimal fundReturn;
  @NotNull private BigDecimal modelReturn;
  @NotNull private BigDecimal tdGeometric;

  private BigDecimal scalingFactor;
  private BigDecimal mgmtFeeDrag;
  private BigDecimal depotFeeDrag;
  private BigDecimal cashDrag;
  private BigDecimal nonSecurityDrag;
  private BigDecimal weightDeviation;
  private BigDecimal transactionCosts;
  private BigDecimal residual;

  private Integer businessDays;
  private BigDecimal avgAum;
  private BigDecimal avgCashPct;

  @NotNull
  @Builder.Default
  @JdbcTypeCode(JSON)
  private Map<String, Object> checks = Map.of();

  @Builder.Default
  @OneToMany(mappedBy = "attribution", cascade = ALL, orphanRemoval = true, fetch = EAGER)
  private List<TdAttributionDetail> details = new ArrayList<>();

  private Instant createdAt;

  @PrePersist
  protected void onCreate() {
    if (createdAt == null) {
      createdAt = clock().instant();
    }
  }

  void addDetail(TdAttributionDetail detail) {
    detail.setAttribution(this);
    details.add(detail);
  }
}
