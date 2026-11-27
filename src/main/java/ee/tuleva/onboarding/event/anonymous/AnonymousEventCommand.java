package ee.tuleva.onboarding.event.anonymous;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AnonymousEventCommand {

  private static final Pattern VISIT_ID = Pattern.compile("^[A-Za-z0-9-]{8,64}$");
  private static final Set<String> DEVICES = Set.of("mobile", "tablet", "desktop");
  private static final int MAX_VALUE_LENGTH = 120;
  // A personal code, phone number or email must never reach this table
  private static final Pattern PERSONAL_DATA = Pattern.compile("\\d{7,}|@");

  private @Nullable AnonymousEventType type;
  private @Nullable String visitId;
  private @Nullable String device;
  @Builder.Default private Map<String, @Nullable Object> data = new HashMap<>();

  boolean isAcceptable() {
    if (type == null || visitId == null || !VISIT_ID.matcher(visitId).matches()) {
      return false;
    }
    if (device != null && !DEVICES.contains(device)) {
      return false;
    }
    if (data == null || !type.allowedKeys().containsAll(data.keySet())) {
      return false;
    }
    return data.values().stream().allMatch(AnonymousEventCommand::isAcceptableValue);
  }

  private static boolean isAcceptableValue(@Nullable Object value) {
    return value instanceof String text
        && text.length() <= MAX_VALUE_LENGTH
        && !PERSONAL_DATA.matcher(text).find();
  }
}
