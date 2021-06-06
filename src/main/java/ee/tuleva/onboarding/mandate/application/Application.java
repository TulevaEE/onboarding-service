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

  protected Long id;
  protected Instant creationTime;
  protected ApplicationType type;
  protected ApplicationStatus status;
  protected ApplicationDetails details;

  @JsonIgnore
  public Integer getPillar() {
    return getDetails().getPillar();
  }

  @JsonIgnore
  public boolean isPending() {
    return status == PENDING;
  }

  @JsonIgnore
  public boolean isWithdrawal() {
    return type == WITHDRAWAL || type == EARLY_WITHDRAWAL;
  }

  @JsonIgnore
  public boolean isTransfer() {
    return type == TRANSFER;
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
