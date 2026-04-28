package ee.tuleva.onboarding.investment.check.health;

import static jakarta.persistence.EnumType.STRING;

import ee.tuleva.onboarding.fund.TulevaFund;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

@Data
@Builder
@Entity
@Table(name = "investment_unit_reconciliation_threshold")
@AllArgsConstructor
@NoArgsConstructor
public class UnitReconciliationThreshold {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(STRING)
  @Column(name = "fund_code", nullable = false, unique = true)
  private TulevaFund fundCode;

  @Column(name = "warning_units", nullable = false)
  private BigDecimal warningUnits;

  @Nullable
  @Column(name = "fail_units")
  private BigDecimal failUnits;

  @Column(name = "created_at")
  private Instant createdAt;

  @Column(name = "updated_at")
  private Instant updatedAt;
}
