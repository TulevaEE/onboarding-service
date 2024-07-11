package ee.tuleva.onboarding.mandate.application;

import com.fasterxml.jackson.annotation.JsonIgnore;
import ee.tuleva.onboarding.epis.mandate.ApplicationStatus;
import java.time.Instant;
import lombok.Builder;
import lombok.Data;
import org.apache.commons.lang3.builder.CompareToBuilder;
import org.jetbrains.annotations.NotNull;

@Data
@Builder
public class Application<T extends ApplicationDetails> implements Comparable<Application<?>> {

  private final Long id;
  private final Instant creationTime;
  private final ApplicationStatus status;
  private final T details;

  public ApplicationType getType() {
    return details != null ? details.getType() : null;
  }

  @JsonIgnore
  public Integer getPillar() {
    return getDetails().getPillar();
  }

  @JsonIgnore
  public boolean isPending() {
    return status != null && status.isPending();
  }

  @JsonIgnore
  public boolean isComplete() {
    return status != null && status.isComplete();
  }

  @JsonIgnore
  public boolean isWithdrawal() {
    return getType() != null && getType().isWithdrawal();
  }

  @JsonIgnore
  public boolean isTransfer() {
    return getType() != null && getType().isTransfer();
  }

  @JsonIgnore
  public boolean hasStatus(ApplicationStatus status) {
    return this.status == status;
  }

  @Override
  public int compareTo(@NotNull Application<?> application) {
    return new CompareToBuilder()
        .append(creationTime, application.creationTime)
        .append(getType(), application.getType())
        .append(status, application.status)
        .append(id, application.id)
        .toComparison();
  }
}
