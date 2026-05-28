package ee.tuleva.onboarding.investment.report.publishing;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public record InvestmentReportPublishingResult(
    Map<String, String> wordPressUrls,
    @Nullable String gitHubPrUrl,
    boolean emailSent,
    List<String> errors) {}
