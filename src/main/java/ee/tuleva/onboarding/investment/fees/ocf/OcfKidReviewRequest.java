package ee.tuleva.onboarding.investment.fees.ocf;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.YearMonth;

public record OcfKidReviewRequest(
    @NotNull TulevaFund fund,
    @NotNull YearMonth month,
    @Positive int version,
    @NotBlank @Size(max = 64) String calculationFingerprint,
    @NotNull Long publicationId,
    @NotBlank @Size(max = 1000) String reason,
    @NotBlank @Size(max = 200) String reviewedBy)
    implements RequestedCalculation {}
