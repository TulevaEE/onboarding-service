package ee.tuleva.onboarding.banking.payment;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class EndToEndIdConverter {

  public String toEndToEndId(UUID uuid) {
    return uuid.toString().replace("-", "");
  }

  public Optional<UUID> toUuid(String endToEndId) {
    if (endToEndId == null || endToEndId.length() != 32) {
      return Optional.empty();
    }
    try {
      String formatted =
          endToEndId.substring(0, 8)
              + "-"
              + endToEndId.substring(8, 12)
              + "-"
              + endToEndId.substring(12, 16)
              + "-"
              + endToEndId.substring(16, 20)
              + "-"
              + endToEndId.substring(20, 32);
      return Optional.of(UUID.fromString(formatted));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}
