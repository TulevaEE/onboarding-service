package ee.tuleva.onboarding.investment.fees.ocf;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

public record OcfPublicationRequest(
    @NotNull TulevaFund fund,
    @NotNull YearMonth month,
    @Positive int version,
    @NotBlank @Size(max = 64) String calculationFingerprint,
    @NotNull LocalDate validFrom,
    @NotNull BigDecimal publishedOcf,
    @NotBlank @Size(max = 200) String publishedIn,
    @NotBlank @Size(max = 200) String publishedBy,
    @NotBlank @Size(max = 1000) String basisReason)
    implements RequestedCalculation {}
