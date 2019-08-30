package ee.tuleva.onboarding.epis.fund;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NavDto {
    private String isin;
    private LocalDate date;
    private BigDecimal value;
}
