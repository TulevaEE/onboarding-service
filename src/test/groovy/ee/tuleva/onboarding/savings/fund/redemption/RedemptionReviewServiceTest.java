package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.IN_REVIEW;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.VERIFIED;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequestFixture.redemptionRequestFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.time.TestClockHolder;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RedemptionReviewServiceTest {

  @Mock private RedemptionRequestRepository repository;
  @Mock private RedemptionStatusService redemptionStatusService;

  @InjectMocks private RedemptionReviewService service;

  @BeforeEach
  void setUp() {
    ClockHolder.setClock(TestClockHolder.clock);
  }

  @AfterEach
  void tearDown() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void approve_recordsApproverAndVerifiesRedemptionInReview() {
    var requestId = UUID.randomUUID();
    var request = redemptionRequestFixture().id(requestId).status(IN_REVIEW).build();
    given(repository.findByIdForUpdate(requestId)).willReturn(Optional.of(request));

    service.approve(requestId, "AML Specialist", "Reviewed transactions, source of funds clear");

    assertThat(request.getReviewedBy()).isEqualTo("AML Specialist");
    assertThat(request.getReviewReason()).isEqualTo("Reviewed transactions, source of funds clear");
    assertThat(request.getReviewedAt()).isEqualTo(TestClockHolder.now);
    verify(repository).save(request);
    verify(redemptionStatusService).changeStatus(requestId, VERIFIED);
  }

  @Test
  void approve_rejectsRedemptionThatIsNotInReview() {
    var requestId = UUID.randomUUID();
    var request = redemptionRequestFixture().id(requestId).status(VERIFIED).build();
    given(repository.findByIdForUpdate(requestId)).willReturn(Optional.of(request));

    assertThatThrownBy(() -> service.approve(requestId, "AML Specialist", "reason"))
        .isInstanceOf(IllegalStateException.class);

    verify(repository, never()).save(request);
    verify(redemptionStatusService, never()).changeStatus(requestId, VERIFIED);
  }

  @Test
  void approve_throwsWhenRedemptionNotFound() {
    var requestId = UUID.randomUUID();
    given(repository.findByIdForUpdate(requestId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.approve(requestId, "AML Specialist", "reason"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
