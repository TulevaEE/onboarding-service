package ee.tuleva.onboarding.savings.fund;

import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.party.ParentChildLinkRegisteredEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChildOnboardingSeedListenerTest {

  @Mock private SavingsFundOnboardingService savingsFundOnboardingService;
  @InjectMocks private ChildOnboardingSeedListener listener;

  @Test
  void onParentChildLinkRegistered_seedsChildOnboarding() {
    listener.onParentChildLinkRegistered(new ParentChildLinkRegisteredEvent("60001019906"));

    verify(savingsFundOnboardingService).seedPersonOnboardingIfAbsent("60001019906");
  }
}
