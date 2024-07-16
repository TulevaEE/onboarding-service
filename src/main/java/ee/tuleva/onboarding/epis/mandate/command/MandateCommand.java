package ee.tuleva.onboarding.epis.mandate.command;

import ee.tuleva.onboarding.epis.mandate.GenericMandateDto;

public class MandateCommand extends MandateInProcess {
  private final GenericMandateDto mandateDto;

  public MandateCommand(String processId, GenericMandateDto mandateDto) {
    super(processId);
    this.mandateDto = mandateDto;
  }

  public GenericMandateDto getMandateDto() {
    return mandateDto;
  }
}
