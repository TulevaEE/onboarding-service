package ee.tuleva.onboarding.capital.event.organisation;

import static jakarta.persistence.EnumType.STRING;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
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
