package ee.tuleva.onboarding.aml.risklevel;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskLevelResult {
  private String personalId;
  private int riskLevel;
  private Map<String, Object> metadata;
}
