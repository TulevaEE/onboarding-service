package ee.tuleva.onboarding.event;

import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TrackedEventCommand {

  private TrackableEventType type;

  @Builder.Default private Map<String, @Nullable Object> data = new HashMap<>();
}
