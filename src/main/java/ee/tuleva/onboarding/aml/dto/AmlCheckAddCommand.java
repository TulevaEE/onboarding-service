package ee.tuleva.onboarding.aml.dto;

import ee.tuleva.onboarding.aml.AmlCheckType;
import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
// Private to prevent Jackson 3 from using it, which would bypass @Builder.Default field
// initializers (e.g. metadata = new HashMap<>()) by passing null for absent JSON fields.
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor
public class AmlCheckAddCommand {
  @ValidAmlCheckType private AmlCheckType type;
  private boolean success;

  @Builder.Default private Map<String, Object> metadata = new HashMap<>();
}
