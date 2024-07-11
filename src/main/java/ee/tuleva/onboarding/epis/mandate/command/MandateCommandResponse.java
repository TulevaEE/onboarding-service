package ee.tuleva.onboarding.epis.mandate.command;

public class MandateCommandResponse extends MandateInProcess {

  private boolean successful;
  private Integer errorCode;
  private String errorMessage;

  public MandateCommandResponse(String processId) {
    super(processId);
  }
}
