package ee.tuleva.onboarding.epis.cashflows;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CashFlowStatement {
    private Map<String, CashFlow> startBalance;
    private Map<String, CashFlow> endBalance;

    private List<CashFlow> transactions;
}