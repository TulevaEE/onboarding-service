package ee.tuleva.onboarding.mandate.application;

import static ee.tuleva.onboarding.epis.mandate.ApplicationStatus.PENDING;
import static ee.tuleva.onboarding.mandate.application.ApplicationType.EARLY_WITHDRAWAL;
import static ee.tuleva.onboarding.mandate.application.ApplicationType.TRANSFER;
import static ee.tuleva.onboarding.mandate.application.ApplicationType.WITHDRAWAL;

import com.fasterxml.jackson.annotation.JsonIgnore;
import ee.tuleva.onboarding.epis.mandate.ApplicationStatus;
import java.time.Instant;
import lombok.Data;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.builder.CompareToBuilder;
import org.jetbrains.annotations.NotNull;

@Data
@SuperBuilder
public class Application implements Comparable<Application> {

  protected final Long id;
  protected final Instant creationTime;
  protected final ApplicationType type;
  protected final ApplicationStatus status;
  protected final ApplicationDetails details;

  @JsonIgnore
  public Integer getPillar() {
    return getDetails().getPillar();
  }

  @JsonIgnore
  public boolean isPending() {
    return getStatus() == PENDING;
  }

  @JsonIgnore
  public boolean isWithdrawal() {
    return getType() == WITHDRAWAL || getType() == EARLY_WITHDRAWAL;
  }

  @JsonIgnore
  public boolean isTransfer() {
    return getType() == TRANSFER;
  }

  @Override
  public int compareTo(@NotNull Application application) {
    return new CompareToBuilder()
        .append(creationTime, application.creationTime)
        .append(type, application.type)
        .append(status, application.status)
        .append(id, application.id)
        .toComparison();
  }
}
