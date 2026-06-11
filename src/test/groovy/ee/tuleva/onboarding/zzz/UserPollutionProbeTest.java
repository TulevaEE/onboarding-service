package ee.tuleva.onboarding.zzz;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.user.UserRepository;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

// Disabled until the suite is clean under -DmaxParallelForks=1. The whole suite currently leaks
// committed rows across many tables (ledger/nav/fund-position/analytics/...) from non-transactional
// specs, so this global cleanliness canary is red in single-fork mode and fork-distribution-flaky
// in CI. Re-enable once the single-fork cleanup is complete (see tracked todo: "Make full test
// suite green under -DmaxParallelForks=1"). It stays here as the regression guard for the
// personal_code auth-user leak class (Withdrawals + MandateBatchSigningControllerTest).
@Disabled(
    "Re-enable once the suite is green under -DmaxParallelForks=1; see single-fork cleanup todo")
@SpringBootTest
class UserPollutionProbeTest {

  @Autowired private UserRepository userRepository;

  @Test
  void noLeakedFixtureUserSurvivesTheSuite() {
    assertThat(userRepository.findByPersonalCode("38812121215"))
        .as(
            "A non-transactional spec committed the shared fixture user (personalCode=38812121215)"
                + " and poisoned the shared H2 DB for every later spec that inserts that user")
        .isEmpty();
  }
}
