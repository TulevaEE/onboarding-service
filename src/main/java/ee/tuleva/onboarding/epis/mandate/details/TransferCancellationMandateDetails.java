package ee.tuleva.onboarding.epis.mandate.details;

import static ee.tuleva.onboarding.mandate.application.ApplicationType.CANCELLATION;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import ee.tuleva.onboarding.mandate.FundTransferExchange;
import ee.tuleva.onboarding.mandate.MandateType;
import ee.tuleva.onboarding.mandate.application.ApplicationType;
import ee.tuleva.onboarding.pillar.Pillar;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Getter;

@Getter
public class TransferCancellationMandateDetails extends MandateDetails {
  @NotNull private final String sourceFundIsinOfTransferToCancel;
  @NotNull private final Pillar pillar;

  @JsonCreator
  public TransferCancellationMandateDetails(
      @JsonProperty("sourceFundIsinOfTransferToCancel") String sourceFundIsinOfTransferToCancel,
      @JsonProperty("pillar") Pillar pillar) {
    super(MandateType.TRANSFER_CANCELLATION);

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
          fundTransferExchanges.getFirst().getSourceFundIsin(), Pillar.fromInt(pillar));
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

  @Override
  public ApplicationType getApplicationType() {
    return CANCELLATION;
  }
}
