package ee.tuleva.onboarding.analytics.transaction.unitowner;

import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class UnitOwnerBalanceEmbeddable {
  private String securityShortName;
  private String securityName;
  private String type;
  private BigDecimal amount;
  private LocalDate startDate;
  private LocalDate lastUpdated;
}
