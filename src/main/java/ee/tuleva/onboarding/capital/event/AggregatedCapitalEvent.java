package ee.tuleva.onboarding.capital.event;

import static javax.persistence.EnumType.STRING;

import ee.tuleva.onboarding.capital.event.organisation.OrganisationCapitalEventType;
import java.math.BigDecimal;
import java.time.LocalDate;
import javax.persistence.Entity;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@Entity
@Table(name = "aggregated_capital_event")
@AllArgsConstructor
@NoArgsConstructor
public class AggregatedCapitalEvent {
  @Id private Long id;

  @Enumerated(STRING)
  private OrganisationCapitalEventType type;

  private BigDecimal fiatValue;
  private BigDecimal totalFiatValue;
  private BigDecimal totalOwnershipUnitAmount;
  private BigDecimal ownershipUnitPrice;
  private LocalDate date;
}
