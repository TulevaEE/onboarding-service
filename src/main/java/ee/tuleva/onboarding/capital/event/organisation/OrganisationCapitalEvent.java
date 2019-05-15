package ee.tuleva.onboarding.capital.event.organisation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
@Builder
@Entity
@Table(name = "organisation_capital_event")
@AllArgsConstructor
@NoArgsConstructor
public class OrganisationCapitalEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    private OrganisationCapitalEventType type;

    @NotNull
    private BigDecimal fiatValue;

}
