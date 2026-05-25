package ee.tuleva.onboarding.statistics;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(InvestorCountGuardrailProperties.class)
public class InvestorStatisticsConfiguration {}
