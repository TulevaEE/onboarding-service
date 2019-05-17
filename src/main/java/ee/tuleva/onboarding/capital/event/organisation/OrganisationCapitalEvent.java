package ee.tuleva.onboarding.capital.event.organisation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

import static javax.persistence.EnumType.STRING;

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
    @Enumerated(STRING)
    private OrganisationCapitalEventType type;

    @NotNull
    private BigDecimal fiatValue;

    @NotNull
    private LocalDate date;
}
