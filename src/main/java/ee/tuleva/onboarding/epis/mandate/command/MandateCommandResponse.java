package ee.tuleva.onboarding.epis.mandate.command;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import ee.tuleva.onboarding.mandate.MandateProcessResult;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

@Getter
public class MandateCommandResponse extends MandateInProcess {

  private final boolean successful;
  private final @Nullable Integer errorCode; // TODO remove when implementing command that creates
  // multiple mandates
  private final @Nullable String errorMessage; // TODO remove when 👆

  @JsonCreator
  public MandateCommandResponse(
      @JsonProperty("processId") String processId,
      @JsonProperty("successful") boolean successful,
      @JsonProperty("errorCode") @Nullable Integer errorCode,
      @JsonProperty("errorMessage") @Nullable String errorMessage) {
    super(processId);

    this.successful = successful;
    this.errorCode = errorCode;
    this.errorMessage = errorMessage;
  }

  public MandateProcessResult toProcessResult() {
    return MandateSubmissionCommandMapper.toProcessResult(this);
  }
}
