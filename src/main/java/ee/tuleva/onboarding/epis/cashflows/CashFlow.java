package ee.tuleva.onboarding.epis.cashflows;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CashFlow {
    private String isin;
    private LocalDate date;
    private BigDecimal amount;
    private String currency;
    private Type type;

    public enum Type {
        CONTRIBUTION;
    }
}