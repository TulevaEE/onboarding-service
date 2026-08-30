package ee.tuleva.onboarding.mandate;

import ee.tuleva.onboarding.country.Country;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

@Builder
public record LegacyMandateSubmission(
    Long id,
    @Nullable String processId,
    @Nullable String futureContributionFundIsin,
    Instant createdDate,
    Integer pillar,
    List<FundTransferInstruction> fundTransferExchanges,
    @Nullable Country address,
    String email,
    String phoneNumber,
    @Nullable BigDecimal paymentRate) {

  public record FundTransferInstruction(
      @Nullable String processId,
      @Nullable BigDecimal amount,
      @Nullable String sourceFundIsin,
      @Nullable String targetFundIsin,
      @Nullable String targetPik) {}
}
