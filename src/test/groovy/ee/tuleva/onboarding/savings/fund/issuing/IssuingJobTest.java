package ee.tuleva.onboarding.savings.fund.issuing;

import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IssuingJobTest {

  private IssuerService issuerService;
  private IssuingJob issuingJob;

  @BeforeEach
  void setUp() {

    issuerService = mock(IssuerService.class);

    issuingJob = new IssuingJob(issuerService);
  }

  @Disabled
  @Test
  @DisplayName("processes payments")
  void processMessages() {
    // TODO
  }
}
