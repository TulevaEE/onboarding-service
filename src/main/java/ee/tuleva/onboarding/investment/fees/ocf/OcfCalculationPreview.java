package ee.tuleva.onboarding.investment.fees.ocf;

import java.math.BigDecimal;
import java.time.YearMonth;
import org.jspecify.annotations.Nullable;

public record OcfCalculationPreview(
    String fundCode,
    YearMonth month,
    int version,
    boolean complete,
    @Nullable String checks,
    String calculationFingerprint,
    BigDecimal totalOcf,
    BigDecimal managementFeeRate,
    int publishedDecimals,
    @Nullable KidFigure kidInForceAtMonthEnd,
    boolean differsFromTheKid,
    @Nullable ExceptionDecision exception,
    @Nullable KidReview keptOnReview,
    boolean alreadyPublished) {}
