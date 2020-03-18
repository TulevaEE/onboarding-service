package ee.tuleva.onboarding.epis.fund;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NavDto {
  private String isin;
  private LocalDate date;
  private BigDecimal value;
}
