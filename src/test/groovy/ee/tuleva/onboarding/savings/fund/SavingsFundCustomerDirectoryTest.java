package ee.tuleva.onboarding.savings.fund;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SavingsFundCustomerDirectoryTest {

  @Mock private SavingsFundOnboardingRepository savingsFundOnboardingRepository;
  @InjectMocks private SavingsFundCustomerDirectory customerDirectory;

  @Test
  void listsTheOnboardedCustomerPersonalCodes() {
    given(savingsFundOnboardingRepository.findPersonCodes())
        .willReturn(List.of("38888888888", "48888888880"));

    assertThat(customerDirectory.personalCodes()).containsExactly("38888888888", "48888888880");
  }
}
