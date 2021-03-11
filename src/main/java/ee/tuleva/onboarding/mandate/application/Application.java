package ee.tuleva.onboarding.mandate.application;

import ee.tuleva.onboarding.epis.mandate.ApplicationStatus;
import java.time.Instant;
import lombok.Builder;
import lombok.Data;
import lombok.val;
import org.jetbrains.annotations.NotNull;

@Data
@Builder
public class Application implements Comparable<Application> {

  private Long id;
  private Instant creationTime;
  private ApplicationType type;
  private ApplicationStatus status;
  private ApplicationDetails details;

  @Override
  public int compareTo(@NotNull Application application) {
    val time = creationTime.compareTo(application.creationTime);
    if (time != 0) {
      return time;
    }
    val typeValue = type.compareTo(application.type);
    if (typeValue != 0) {
      return typeValue;
    }
    return status.compareTo(application.status);
  }
}
