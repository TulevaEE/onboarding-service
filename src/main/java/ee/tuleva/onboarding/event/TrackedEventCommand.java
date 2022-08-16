package ee.tuleva.onboarding.event;

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
public class TrackedEventCommand {

  private String type;

  @Builder.Default private Map<String, Object> data = new HashMap<>();
}
