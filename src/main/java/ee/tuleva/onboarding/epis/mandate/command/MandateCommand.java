package ee.tuleva.onboarding.epis.mandate.command;

import ee.tuleva.onboarding.epis.mandate.GenericMandateDto;
import ee.tuleva.onboarding.epis.mandate.details.MandateDetails;

public class MandateCommand<TDetails extends MandateDetails> extends MandateInProcess {
  private final GenericMandateDto<TDetails> mandateDto;

  public MandateCommand(String processId, GenericMandateDto<TDetails> mandateDto) {
    super(processId);
    this.mandateDto = mandateDto;
  }

  public GenericMandateDto<TDetails> getMandateDto() {
    return mandateDto;
  }
}
