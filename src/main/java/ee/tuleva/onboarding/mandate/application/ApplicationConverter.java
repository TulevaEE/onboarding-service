package ee.tuleva.onboarding.mandate.application;

import static ee.tuleva.onboarding.mandate.application.ApplicationType.TRANSFER;

import ee.tuleva.onboarding.epis.mandate.ApplicationDTO;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ApplicationConverter implements Converter<ApplicationDTO, Application> {

  @Override
  public Application convert(ApplicationDTO applicationDTO) {
    val applicationBuilder =
        Application.builder()
            .type(applicationDTO.getType())
            .status(applicationDTO.getStatus())
            .id(applicationDTO.getId());
    if (applicationDTO.getType().equals(TRANSFER)) {
      addTransferExchange(applicationBuilder, applicationDTO);
    }
    return applicationBuilder.build();
  }

  private void addTransferExchange(
      Application.ApplicationBuilder applicationBuilder, ApplicationDTO applicationDTO) {
    applicationBuilder.details(
        TransferApplicationDetails.builder()
            .amount(applicationDTO.getAmount())
            .currency(applicationDTO.getCurrency())
            .date(applicationDTO.getDate())
            .sourceFundIsin(applicationDTO.getSourceFundIsin())
            .targetFundIsin(applicationDTO.getTargetFundIsin())
            .build());
  }
}
