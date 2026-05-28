package ee.tuleva.onboarding.investment.check.tracking;

import static jakarta.persistence.FetchType.LAZY;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@Builder
@Entity
@Table(name = "investment_td_attribution_detail")
@AllArgsConstructor
@NoArgsConstructor
class TdAttributionDetail {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ToString.Exclude
  @EqualsAndHashCode.Exclude
  @ManyToOne(fetch = LAZY)
  @JoinColumn(name = "attribution_id", nullable = false)
  private PeriodicTdAttribution attribution;

  @NotNull private String isin;

  private String instrumentName;
  private BigDecimal modelWeight;
  private BigDecimal avgActualWeight;
  private BigDecimal weightDevContribution;
  private BigDecimal securityReturn;
}
