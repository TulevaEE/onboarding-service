package ee.tuleva.onboarding.mandate.application;

import com.fasterxml.jackson.annotation.JsonIgnore;
import ee.tuleva.onboarding.epis.mandate.ApplicationStatus;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Data;
import org.apache.commons.lang3.compare.ObjectToStringComparator;
import org.jetbrains.annotations.NotNull;

@Data
@Builder
public class Application implements Comparable<Application> {

  private Long id;
  private Instant creationTime;
  private ApplicationType type;
  private ApplicationStatus status;
  private ApplicationDetails details;
  private final LocalDate cancellationDeadline;
  private final LocalDate fulfillmentDate;

  @JsonIgnore
  public boolean isPending() {
    return status.equals(ApplicationStatus.PENDING);
  }

  @JsonIgnore
  public boolean isWithdrawal() {
    return type.equals(ApplicationType.WITHDRAWAL) || type.equals(ApplicationType.EARLY_WITHDRAWAL);
  }

  @Override
  public int compareTo(@NotNull Application application) {
    return ObjectToStringComparator.INSTANCE.compare(this, application);
  }
}
