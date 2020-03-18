package ee.tuleva.onboarding.aml.command;

import ee.tuleva.onboarding.aml.AmlCheckType;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AmlCheckAddCommand {
  @ValidAmlCheckType private AmlCheckType type;
  private boolean success;
  @Builder.Default private Map<String, Object> metadata = new HashMap<>();
}
