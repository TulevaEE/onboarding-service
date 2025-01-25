package ee.tuleva.onboarding.aml.risklevel;

import java.util.Map;
import lombok.Value;

@Value
public class RiskLevelResult {
  String personalId;
  Integer riskLevel;
  Map<String, Object> metadata;
}
