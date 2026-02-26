package ee.tuleva.onboarding.epis.mandate.command;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class MandateCommandResponse extends MandateInProcess {

  private boolean successful;
  private Integer errorCode; // TODO remove when implementing command that creates multiple mandates
  private String errorMessage; // TODO remove when 👆

  public MandateCommandResponse() {
    super(null);
  }

  public MandateCommandResponse(String processId, boolean successful) {
    super(processId);

    this.successful = successful;
  }

  @JsonCreator
  public MandateCommandResponse(
      @JsonProperty("processId") String processId,
      @JsonProperty("successful") boolean successful,
      @JsonProperty("errorCode") Integer errorCode,
      @JsonProperty("errorMessage") String errorMessage) {
    super(processId);

    this.successful = successful;
    this.errorCode = errorCode;
    this.errorMessage = errorMessage;
  }
}
