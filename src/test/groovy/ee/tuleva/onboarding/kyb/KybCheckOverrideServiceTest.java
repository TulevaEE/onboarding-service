package ee.tuleva.onboarding.kyb;

import static ee.tuleva.onboarding.kyb.KybCheckType.SINGLE_BOARD_MEMBER_OWNERSHIP;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class KybCheckOverrideServiceTest {

  private static final Instant FIXED_NOW = Instant.parse("2026-07-22T10:00:00Z");
  private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

  private final KybCheckOverrideRepository kybCheckOverrideRepository =
      mock(KybCheckOverrideRepository.class);

  private final KybCheckOverrideService kybCheckOverrideService =
      new KybCheckOverrideService(kybCheckOverrideRepository, FIXED_CLOCK);

  @Test
  void forceSuccess_createsOverrideWhenNoneExists() {
    given(
            kybCheckOverrideRepository.findByRegistryCodeAndCheckType(
                "12934765", SINGLE_BOARD_MEMBER_OWNERSHIP))
        .willReturn(Optional.empty());

    kybCheckOverrideService.forceSuccess(
        "12934765",
        SINGLE_BOARD_MEMBER_OWNERSHIP,
        "single shareholder, two spousal beneficial owners");

    verify(kybCheckOverrideRepository)
        .save(
            argThat(
                override ->
                    override.getRegistryCode().equals("12934765")
                        && override.getCheckType() == SINGLE_BOARD_MEMBER_OWNERSHIP
                        && override.isForcedSuccess()
                        && override
                            .getReason()
                            .equals("single shareholder, two spousal beneficial owners")
                        && override.getExpiresAt().equals(FIXED_NOW.plus(Duration.ofDays(365)))
                        && override.getCreatedBy().equals("admin")));
  }

  @Test
  void forceSuccess_withExplicitExpiry_savesGivenExpiry() {
    var explicitExpiry = Instant.parse("2027-01-01T00:00:00Z");
    given(
            kybCheckOverrideRepository.findByRegistryCodeAndCheckType(
                "12934765", SINGLE_BOARD_MEMBER_OWNERSHIP))
        .willReturn(Optional.empty());

    kybCheckOverrideService.forceSuccess(
        "12934765",
        SINGLE_BOARD_MEMBER_OWNERSHIP,
        "single shareholder, two spousal beneficial owners",
        explicitExpiry);

    verify(kybCheckOverrideRepository)
        .save(argThat(override -> override.getExpiresAt().equals(explicitExpiry)));
  }

  @Test
  void forceSuccess_updatesExistingOverride() {
    var existingOverride =
        KybCheckOverride.builder()
            .registryCode("12934765")
            .checkType(SINGLE_BOARD_MEMBER_OWNERSHIP)
            .forcedSuccess(false)
            .reason("old reason")
            .expiresAt(Instant.parse("2026-08-01T00:00:00Z"))
            .createdBy("admin")
            .build();
    var explicitExpiry = Instant.parse("2027-06-01T00:00:00Z");
    given(
            kybCheckOverrideRepository.findByRegistryCodeAndCheckType(
                "12934765", SINGLE_BOARD_MEMBER_OWNERSHIP))
        .willReturn(Optional.of(existingOverride));

    kybCheckOverrideService.forceSuccess(
        "12934765", SINGLE_BOARD_MEMBER_OWNERSHIP, "new reason", explicitExpiry);

    verify(kybCheckOverrideRepository)
        .save(
            argThat(
                override ->
                    override == existingOverride
                        && override.isForcedSuccess()
                        && override.getReason().equals("new reason")
                        && override.getExpiresAt().equals(explicitExpiry)));
  }
}
