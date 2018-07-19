package ee.tuleva.onboarding.epis.cashflows;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CashFlowValueDto {
    private Instant time;
    private BigDecimal amount;
    private String currency;
}