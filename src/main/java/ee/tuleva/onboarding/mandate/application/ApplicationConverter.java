package ee.tuleva.onboarding.mandate.application;

import ee.tuleva.onboarding.epis.mandate.ApplicationDTO;
import ee.tuleva.onboarding.fund.FundRepository;
import ee.tuleva.onboarding.fund.response.FundDto;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ApplicationConverter {
    private final FundRepository fundRepository;

    public Application convert(ApplicationDTO applicationDTO, String language) {
        val applicationBuilder = Application.builder()
            .type(applicationDTO.getType())
            .status(applicationDTO.getStatus())
            .id(applicationDTO.getId());
        if (applicationDTO.getType().equals(ApplicationType.TRANSFER)) {
            addTransferExchange(applicationBuilder, applicationDTO, language);
        }
        return applicationBuilder.build();
    }

    private void addTransferExchange(
        Application.ApplicationBuilder applicationBuilder,
        ApplicationDTO applicationDTO,
        String language
    ) {
        applicationBuilder.details(TransferApplicationDetails.builder()
            .amount(applicationDTO.getAmount())
            .currency(applicationDTO.getCurrency())
            .date(applicationDTO.getDate())
            .sourceFund(new FundDto(fundRepository.findByIsin(
                applicationDTO.getSourceFundIsin()
            ), language))
            .targetFund(new FundDto(fundRepository.findByIsin(
                applicationDTO.getTargetFundIsin()
                ), language)
            )
            .build());
    }

}
