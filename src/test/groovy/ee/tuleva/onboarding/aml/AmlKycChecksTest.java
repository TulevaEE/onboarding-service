package ee.tuleva.onboarding.aml;

import static ee.tuleva.onboarding.aml.AmlCheckType.KYC_CHECK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AmlKycChecksTest {

  @Mock private AmlCheckRepository amlCheckRepository;
  @InjectMocks private AmlKycChecks kycChecks;

  @Test
  void reportsTheLatestRecentKycCheckResult() {
    given(
            amlCheckRepository
                .findFirstByPersonalCodeAndTypeAndCreatedTimeAfterOrderByCreatedTimeDescIdDesc(
                    eq("38888888888"), eq(KYC_CHECK), any(Instant.class)))
        .willReturn(Optional.of(AmlCheck.builder().type(KYC_CHECK).success(true).build()));

    assertThat(kycChecks.latestKycCheckPassedWithinLastYear("38888888888")).contains(true);
  }

  @Test
  void reportsEmptyWhenThereIsNoRecentKycCheck() {
    given(
            amlCheckRepository
                .findFirstByPersonalCodeAndTypeAndCreatedTimeAfterOrderByCreatedTimeDescIdDesc(
                    eq("38888888888"), eq(KYC_CHECK), any(Instant.class)))
        .willReturn(Optional.empty());

    assertThat(kycChecks.latestKycCheckPassedWithinLastYear("38888888888")).isEmpty();
  }
}
