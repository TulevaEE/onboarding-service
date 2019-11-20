package ee.tuleva.onboarding.epis.cashflows;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CashFlowStatement {
    @Builder.Default
    private Map<String, CashFlow> startBalance = new HashMap<>();
    @Builder.Default
    private Map<String, CashFlow> endBalance = new HashMap<>();
    @Builder.Default
    private List<CashFlow> transactions = new ArrayList<>();
}