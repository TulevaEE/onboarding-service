package ee.tuleva.onboarding.party;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "guardian-lookup")
public record MinorGuardianLookupProperties(
    String requesterPersonalCode,
    @DefaultValue("1000") int batchSize,
    @DefaultValue("1s") Duration pause) {}
