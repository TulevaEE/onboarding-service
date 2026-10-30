package ee.tuleva.onboarding.investment.fees.ocf;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import org.jspecify.annotations.Nullable;

public record OcfPublicationRequest(
    @NotNull TulevaFund fund,
    @NotNull YearMonth month,
    @Positive int version,
    @NotNull LocalDate validFrom,
    @NotNull BigDecimal publishedOcf,
    @NotBlank @Size(max = 200) String publishedIn,
    @NotBlank @Size(max = 200) String publishedBy,
    @Nullable @Size(max = 200) String approvedBy,
    @Nullable @Size(max = 1000) String exceptionReason) {}
