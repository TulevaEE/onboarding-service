package ee.tuleva.onboarding.epis.mandate.details;

import ee.tuleva.onboarding.mandate.FundTransferExchange;
import ee.tuleva.onboarding.mandate.MandateType;
import java.util.List;
import lombok.Getter;

public class TransferCancellationMandateDetails extends MandateDetails {
  @Getter private final String sourceFundIsinOfTransferToCancel;
  @Getter private final Integer pillar;

  public TransferCancellationMandateDetails(String sourceFundIsinOfTransferToCancel, int pillar) {
    super(MandateType.TRANSFER_CANCELLATION);

    if (pillar != 2 && pillar != 3) {
      throw new IllegalArgumentException("Invalid pillar");
    }

    this.pillar = pillar;
    this.sourceFundIsinOfTransferToCancel = sourceFundIsinOfTransferToCancel;
  }

  public static TransferCancellationMandateDetails fromFundTransferExchanges(
      List<FundTransferExchange> fundTransferExchanges, int pillar) {
    if (fundTransferExchanges != null
        && fundTransferExchanges.size() == 1
        && fundTransferExchanges.getFirst().getSourceFundIsin() != null
        && fundTransferExchanges.getFirst().getTargetFundIsin() == null
        && fundTransferExchanges.getFirst().getAmount() == null) {
      return new TransferCancellationMandateDetails(
          fundTransferExchanges.getFirst().getSourceFundIsin(), pillar);
    } else {
      throw new IllegalArgumentException(
          "Cannot construct TransferCancellationMandateDetails from invalid fundTransferExchanges");
    }
  }

  public List<FundTransferExchange> toFundTransferExchanges() {
    return List.of(
        FundTransferExchange.builder()
            .amount(null)
            .sourceFundIsin(this.sourceFundIsinOfTransferToCancel)
            .targetFundIsin(null)
            .build());
  }
}
