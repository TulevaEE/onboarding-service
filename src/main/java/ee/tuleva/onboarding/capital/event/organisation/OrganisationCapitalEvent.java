package ee.tuleva.onboarding.capital.event.organisation;

import static javax.persistence.EnumType.STRING;

import java.math.BigDecimal;
import java.time.LocalDate;
import javax.persistence.*;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

  @NotNull private BigDecimal fiatValue;

  @NotNull private LocalDate date;
}
