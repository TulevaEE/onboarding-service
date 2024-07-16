package ee.tuleva.onboarding.epis.mandate.command;

import lombok.Getter;

@Getter
public abstract class MandateInProcess {
  private final String processId;

  public MandateInProcess(String processId) {
    this.processId = processId;
  }
}
