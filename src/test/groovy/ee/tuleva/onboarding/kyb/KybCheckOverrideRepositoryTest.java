package ee.tuleva.onboarding.kyb;

import static ee.tuleva.onboarding.kyb.KybCheckType.SINGLE_BOARD_MEMBER_OWNERSHIP;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
class KybCheckOverrideRepositoryTest {

  @Autowired private TestEntityManager entityManager;

  @Autowired private KybCheckOverrideRepository repository;

  @Test
  void savesAndFindsOverrideByRegistryCodeAndCheckType() {
    var expiresAt = Instant.parse("2027-07-22T10:00:00Z");
    var override =
        repository.save(
            KybCheckOverride.builder()
                .registryCode("12934765")
                .checkType(SINGLE_BOARD_MEMBER_OWNERSHIP)
                .forcedSuccess(true)
                .reason("single shareholder, two spousal beneficial owners")
                .expiresAt(expiresAt)
                .createdBy("admin")
                .build());
    entityManager.flush();
    entityManager.clear();

    var reloaded = repository.findById(override.getId()).orElseThrow();
    assertThat(reloaded.getRegistryCode()).isEqualTo("12934765");
    assertThat(reloaded.getCheckType()).isEqualTo(SINGLE_BOARD_MEMBER_OWNERSHIP);
    assertThat(reloaded.isForcedSuccess()).isTrue();
    assertThat(reloaded.getReason()).isEqualTo("single shareholder, two spousal beneficial owners");
    assertThat(reloaded.getExpiresAt()).isEqualTo(expiresAt);
    assertThat(reloaded.getCreatedBy()).isEqualTo("admin");
    assertThat(reloaded.getCreatedTime()).isNotNull();

    assertThat(repository.findByRegistryCode("12934765")).hasSize(1);
    assertThat(repository.findByRegistryCodeAndCheckType("12934765", SINGLE_BOARD_MEMBER_OWNERSHIP))
        .isPresent();
  }
}
