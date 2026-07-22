package ee.tuleva.onboarding.kyb;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class KybCheckOverrideService {

  private static final String CREATED_BY = "admin";
  private static final Duration DEFAULT_VALIDITY = Duration.ofDays(365);

  private final KybCheckOverrideRepository kybCheckOverrideRepository;
  private final Clock clock;

  @Transactional
  public void forceSuccess(String registryCode, KybCheckType checkType, String reason) {
    forceSuccess(registryCode, checkType, reason, clock.instant().plus(DEFAULT_VALIDITY));
  }

  @Transactional
  public void forceSuccess(
      String registryCode, KybCheckType checkType, String reason, Instant expiresAt) {
    var override =
        kybCheckOverrideRepository
            .findByRegistryCodeAndCheckType(registryCode, checkType)
            .orElseGet(
                () ->
                    KybCheckOverride.builder()
                        .registryCode(registryCode)
                        .checkType(checkType)
                        .build());
    override.setForcedSuccess(true);
    override.setReason(reason);
    override.setExpiresAt(expiresAt);
    override.setCreatedBy(CREATED_BY);
    kybCheckOverrideRepository.save(override);
  }
}
