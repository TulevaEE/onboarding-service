package ee.tuleva.onboarding.investment.report.publishing;

import java.util.List;
import java.util.Map;

public record InvestmentReportPublishingResult(
    Map<String, String> wordPressUrls, boolean emailSent, List<String> errors) {}
