package ee.tuleva.onboarding.investment.fees.ocf;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import org.jspecify.annotations.Nullable;

public record OcfPublicationPreview(
    String fundCode,
    YearMonth month,
    int version,
    boolean complete,
    @Nullable String checks,
    BigDecimal calculatedOcf,
    BigDecimal calculatedManagementFeeRate,
    LocalDate validFrom,
    BigDecimal managementFeeRate,
    BigDecimal publishedOcf,
    BigDecimal publishedOcfPercent,
    @Nullable KidFigure kidInForceBefore,
    boolean alreadyPublished) {}
