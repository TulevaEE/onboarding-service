package ee.tuleva.onboarding.epis.mandate.command;

import ee.tuleva.onboarding.epis.mandate.GenericMandateDto;

public class MandateCommand extends MandateInProcess {
  private final GenericMandateDto mandate;

  public MandateCommand(String processId, GenericMandateDto mandate) {
    super(processId);
    this.mandate = mandate;
  }
}
