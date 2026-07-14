package ee.tuleva.onboarding.fund.fees;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pensionikeskus.fees")
record PensionikeskusFeesProperties(
    String secondPillarStatisticsUrl,
    String thirdPillarStatisticsUrl,
    String secondPillarFeeComparisonUrl,
    String thirdPillarFeeComparisonUrl) {}
