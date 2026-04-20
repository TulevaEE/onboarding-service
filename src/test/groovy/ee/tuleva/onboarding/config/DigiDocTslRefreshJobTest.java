package ee.tuleva.onboarding.config;

import static org.mockito.BDDMockito.*;

import org.digidoc4j.Configuration;
import org.digidoc4j.TSLCertificateSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DigiDocTslRefreshJobTest {

  @Mock private Configuration digiDocConfiguration;
  @Mock private TSLCertificateSource tsl;
  @InjectMocks private DigiDocTslRefreshJob digiDocTslRefreshJob;

  @BeforeEach
  void setUp() {
    digiDocTslRefreshJob.backoffBaseSeconds = 0;
  }

  @Test
  void retriesOnTransientFailure() {
    given(digiDocConfiguration.getTSL()).willReturn(tsl);
    willThrow(new RuntimeException("LoTL download failed")).willDoNothing().given(tsl).refresh();

    digiDocTslRefreshJob.refreshTslOnStartup();

    verify(tsl, times(2)).refresh();
  }

  @Test
  void succeedsOnFirstAttempt() {
    given(digiDocConfiguration.getTSL()).willReturn(tsl);

    digiDocTslRefreshJob.refreshTslOnStartup();

    verify(tsl, times(1)).refresh();
  }

  @Test
  void retriesAllAttemptsBeforeGivingUp() {
    given(digiDocConfiguration.getTSL()).willReturn(tsl);
    willThrow(new RuntimeException("LoTL download failed")).given(tsl).refresh();

    digiDocTslRefreshJob.refreshTslOnStartup();

    verify(tsl, times(8)).refresh();
  }

  @Test
  void scheduledRefreshCallsRefreshWithRetry() {
    given(digiDocConfiguration.getTSL()).willReturn(tsl);

    digiDocTslRefreshJob.scheduledRefreshTsl();

    verify(tsl, times(1)).refresh();
  }
}
