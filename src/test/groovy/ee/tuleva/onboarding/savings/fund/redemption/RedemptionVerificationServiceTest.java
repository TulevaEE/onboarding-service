package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.aml.AmlCheck;
import ee.tuleva.onboarding.aml.AmlCheckType;
import ee.tuleva.onboarding.aml.AmlService;
import ee.tuleva.onboarding.country.Country;
import ee.tuleva.onboarding.kyc.survey.KycSurveyService;
import ee.tuleva.onboarding.user.UserService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RedemptionVerificationServiceTest {

  @Mock private UserService userService;
  @Mock private KycSurveyService kycSurveyService;
  @Mock private AmlService amlService;
  @Mock private RedemptionStatusService redemptionStatusService;

  @InjectMocks private RedemptionVerificationService redemptionVerificationService;

  @Test
  @DisplayName("process transitions to VERIFIED when all AML checks pass")
  void process_allChecksPassed_transitionsToVerified() {
    var requestId = UUID.randomUUID();
    var userId = 1L;
    var request = createRequest(requestId, userId);
    var user = sampleUser().id(userId).build();
    var country = new Country("EE");
    var passingCheck =
        AmlCheck.builder()
            .personalCode(user.getPersonalCode())
            .type(AmlCheckType.SANCTION)
            .success(true)
            .build();

    when(userService.getByIdOrThrow(userId)).thenReturn(user);
    when(kycSurveyService.getCountry(userId)).thenReturn(Optional.of(country));
    when(amlService.addSanctionAndPepCheckIfMissing(user, country))
        .thenReturn(List.of(passingCheck));

    redemptionVerificationService.process(request);

    verify(redemptionStatusService).changeStatus(requestId, VERIFIED);
    verify(redemptionStatusService, never()).changeStatus(requestId, IN_REVIEW);
  }

  @Test
  @DisplayName("process transitions to IN_REVIEW when AML check fails")
  void process_checkFails_transitionsToInReview() {
    var requestId = UUID.randomUUID();
    var userId = 1L;
    var request = createRequest(requestId, userId);
    var user = sampleUser().id(userId).build();
    var country = new Country("EE");
    var failingCheck =
        AmlCheck.builder()
            .personalCode(user.getPersonalCode())
            .type(AmlCheckType.SANCTION)
            .success(false)
            .build();

    when(userService.getByIdOrThrow(userId)).thenReturn(user);
    when(kycSurveyService.getCountry(userId)).thenReturn(Optional.of(country));
    when(amlService.addSanctionAndPepCheckIfMissing(user, country))
        .thenReturn(List.of(failingCheck));

    redemptionVerificationService.process(request);

    verify(redemptionStatusService).changeStatus(requestId, IN_REVIEW);
    verify(redemptionStatusService, never()).changeStatus(requestId, VERIFIED);
  }

  @Test
  @DisplayName("process transitions to VERIFIED when no new checks needed")
  void process_noNewChecksNeeded_transitionsToVerified() {
    var requestId = UUID.randomUUID();
    var userId = 1L;
    var request = createRequest(requestId, userId);
    var user = sampleUser().id(userId).build();
    var country = new Country("EE");

    when(userService.getByIdOrThrow(userId)).thenReturn(user);
    when(kycSurveyService.getCountry(userId)).thenReturn(Optional.of(country));
    when(amlService.addSanctionAndPepCheckIfMissing(user, country)).thenReturn(List.of());

    redemptionVerificationService.process(request);

    verify(redemptionStatusService).changeStatus(requestId, VERIFIED);
  }

  @Test
  @DisplayName("process throws when KYC survey country not found")
  void process_noKycCountry_throws() {
    var requestId = UUID.randomUUID();
    var userId = 1L;
    var request = createRequest(requestId, userId);
    var user = sampleUser().id(userId).build();

    when(userService.getByIdOrThrow(userId)).thenReturn(user);
    when(kycSurveyService.getCountry(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> redemptionVerificationService.process(request))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("KYC survey with country not found");

    verify(redemptionStatusService, never()).changeStatus(any(), any());
  }

  private RedemptionRequest createRequest(UUID id, Long userId) {
    return RedemptionRequest.builder()
        .id(id)
        .userId(userId)
        .fundUnits(new BigDecimal("10.00000"))
        .customerIban("EE123456789012345678")
        .status(RESERVED)
        .build();
  }
}
