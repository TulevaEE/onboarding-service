package ee.tuleva.onboarding.epis.mandate.command;

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

  public MandateCommandResponse(
      String processId, boolean successful, Integer errorCode, String errorMessage) {
    super(processId);

    this.successful = successful;
    this.errorCode = errorCode;
    this.errorMessage = errorMessage;
  }
}
