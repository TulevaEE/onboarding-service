package ee.tuleva.onboarding.investment.fees.ocf;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

public record OcfGivenPublicationRequest(
    @NotNull TulevaFund fund,
    @NotNull LocalDate validFrom,
    @NotNull @Positive @DecimalMax("0.1") @Digits(integer = 1, fraction = 8)
        BigDecimal publishedOcf,
    @NotNull @Min(1) @Max(4) Integer publishedDecimals,
    @NotNull OcfPublicationBasis basis,
    @Nullable LocalDate exPostPeriodStart,
    @Nullable LocalDate exPostPeriodEnd,
    @NotBlank @Size(max = 200) String publishedIn,
    @NotBlank @Size(max = 200) String publishedBy,
    @NotBlank @Size(max = 1000) String basisReason) {}
