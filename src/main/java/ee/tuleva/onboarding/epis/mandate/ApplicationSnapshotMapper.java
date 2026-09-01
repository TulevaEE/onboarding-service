package ee.tuleva.onboarding.epis.mandate;

import ee.tuleva.onboarding.epis.mandate.MandateDto.MandateFundsTransferExchangeDTO;
import ee.tuleva.onboarding.mandate.application.ApplicationSnapshot;
import java.util.List;
import org.jspecify.annotations.Nullable;

class ApplicationSnapshotMapper {

  private ApplicationSnapshotMapper() {}

  static ApplicationSnapshot toSnapshot(ApplicationDTO dto) {
    return ApplicationSnapshot.builder()
        .id(dto.getId())
        .date(dto.getDate())
        .documentNumber(dto.getDocumentNumber())
        .status(toApplicationStatus(dto.getStatus()))
        .type(dto.getType())
        .sourceFundIsin(dto.getSourceFundIsin())
        .fundTransferExchanges(toFundTransfers(dto.getFundTransferExchanges()))
        .bankAccount(dto.getBankAccount())
        .paymentRate(dto.getPaymentRate())
        .fundPensionDetails(toFundPensionDetails(dto.getFundPensionDetails()))
        .build();
  }

  private static List<ApplicationSnapshot.FundTransfer> toFundTransfers(
      @Nullable List<MandateFundsTransferExchangeDTO> exchanges) {
    if (exchanges == null) {
      return List.of();
    }
    return exchanges.stream()
        .map(
            exchange ->
                new ApplicationSnapshot.FundTransfer(
                    exchange.getTargetFundIsin(), exchange.getTargetPik(), exchange.getAmount()))
        .toList();
  }

  private static ApplicationSnapshot.@Nullable FundPensionDetails toFundPensionDetails(
      ApplicationDTO.@Nullable FundPensionDetails details) {
    return details == null
        ? null
        : new ApplicationSnapshot.FundPensionDetails(
            details.durationYears(), details.paymentsPerYear());
  }

  private static ee.tuleva.onboarding.mandate.application.ApplicationStatus toApplicationStatus(
      ApplicationStatus status) {
    return switch (status) {
      case COMPLETE -> ee.tuleva.onboarding.mandate.application.ApplicationStatus.COMPLETE;
      case PENDING -> ee.tuleva.onboarding.mandate.application.ApplicationStatus.PENDING;
      case FAILED -> ee.tuleva.onboarding.mandate.application.ApplicationStatus.FAILED;
    };
  }
}
