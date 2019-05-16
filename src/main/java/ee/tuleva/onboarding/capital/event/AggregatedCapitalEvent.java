package ee.tuleva.onboarding.capital.event;

import ee.tuleva.onboarding.capital.event.organisation.OrganisationCapitalEventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@Entity
@Table(name = "aggregated_capital_event")
@AllArgsConstructor
@NoArgsConstructor
public class AggregatedCapitalEvent {
    @Id
    private Long id;
    private OrganisationCapitalEventType type;
    private BigDecimal fiatValue;
    private BigDecimal totalFiatValue;
    private BigDecimal totalOwnershipUnitAmount;
    private BigDecimal ownershipUnitPrice;
    private LocalDate date;
}
