package ee.tuleva.onboarding.epis.mandate.details;

import ee.tuleva.onboarding.mandate.FundTransferExchange;
import ee.tuleva.onboarding.mandate.MandateType;
import java.util.List;
import lombok.Getter;

public class TransferCancellationMandateDetails extends MandateDetails {
  @Getter private final String sourceFundIsinOfTransferToCancel;

  public TransferCancellationMandateDetails(String sourceFundIsinOfTransferToCancel) {
    super(MandateType.TRANSFER_CANCELLATION);

    this.sourceFundIsinOfTransferToCancel = sourceFundIsinOfTransferToCancel;
  }

  public static TransferCancellationMandateDetails fromFundTransferExchanges(
      List<FundTransferExchange> fundTransferExchanges) {
    if (fundTransferExchanges != null
        && fundTransferExchanges.size() == 1
        && fundTransferExchanges.getFirst().getSourceFundIsin() != null
        && fundTransferExchanges.getFirst().getTargetFundIsin() == null
        && fundTransferExchanges.getFirst().getAmount() == null) {
      return new TransferCancellationMandateDetails(
          fundTransferExchanges.getFirst().getSourceFundIsin());
    } else {
      throw new IllegalArgumentException(
          "Cannot construct TransferCancellationMandateDetails from invalid fundTransferExchanges");
    }
  }
}
