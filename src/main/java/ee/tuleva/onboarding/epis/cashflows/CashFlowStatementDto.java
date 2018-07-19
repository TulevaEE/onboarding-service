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
public class CashFlowStatementDto {
    private Map<String, CashFlowValueDto> startBalance;
    private Map<String, CashFlowValueDto> endBalance;

    private List<CashFlowValueDto> transactions;
}