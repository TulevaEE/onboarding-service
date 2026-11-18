package ee.tuleva.onboarding.party;

import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Profile("!dev")
@ConditionalOnProperty(prefix = "guardian-lookup", name = "requester-personal-code")
@EnableConfigurationProperties(MinorGuardianLookupProperties.class)
public class ScheduledMinorGuardianLookupJob {

  private final MinorGuardianLookupService minorGuardianLookupService;
  private final MinorGuardianLookupProperties properties;

  @Scheduled(cron = "0 30 5 * * *", zone = "Europe/Tallinn")
  @SchedulerLock(
      name = "ScheduledMinorGuardianLookupJob_lookUpMinorGuardians",
      lockAtMostFor = "23h",
      lockAtLeastFor = "30m")
  public void lookUpMinorGuardians() {
    minorGuardianLookupService.lookUpMinorThirdPillarHolders(
        properties.requesterPersonalCode(), properties.batchSize(), properties.pause());
  }
}
