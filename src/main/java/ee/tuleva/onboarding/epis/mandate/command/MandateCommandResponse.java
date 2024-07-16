package ee.tuleva.onboarding.epis.mandate.command;

import lombok.Getter;

@Getter
public class MandateCommandResponse extends MandateInProcess {

  private boolean successful;
  private Integer errorCode;
  private String errorMessage;

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
