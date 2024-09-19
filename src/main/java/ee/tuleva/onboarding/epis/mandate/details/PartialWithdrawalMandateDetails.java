package ee.tuleva.onboarding.epis.mandate.details;

import static ee.tuleva.onboarding.mandate.MandateType.PARTIAL_WITHDRAWAL;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import lombok.Getter;

@Getter
public class PartialWithdrawalMandateDetails extends MandateDetails {

  @NotNull private final Pillar pillar;
  @NotNull private final BankAccountDetails bankAccountDetails;
  @NotNull private final List<FundWithdrawalAmount> fundWithdrawalAmounts;

  @Valid3LetterCountryCode private final String taxResidency;

  @JsonCreator
  public PartialWithdrawalMandateDetails(
      @JsonProperty("pillar") Pillar pillar,
      @JsonProperty("bankAccountDetails") BankAccountDetails bankAccountDetails,
      @JsonProperty("fundWithdrawalAmounts") List<FundWithdrawalAmount> fundWithdrawalAmounts,
      @JsonProperty("taxResidency") String taxResidency) {
    super(PARTIAL_WITHDRAWAL);

    this.pillar = pillar;
    this.bankAccountDetails = bankAccountDetails;
    this.fundWithdrawalAmounts = fundWithdrawalAmounts;
    this.taxResidency = taxResidency;
  }

  public record FundWithdrawalAmount(String isin, int percentage, BigDecimal units) {}
}
