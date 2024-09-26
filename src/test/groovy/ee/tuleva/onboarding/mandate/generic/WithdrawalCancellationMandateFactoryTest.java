package ee.tuleva.onboarding.mandate.generic;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.conversion.ConversionResponseFixture.fullyConverted;
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture;
import static ee.tuleva.onboarding.mandate.MandateType.EARLY_WITHDRAWAL_CANCELLATION;
import static ee.tuleva.onboarding.mandate.MandateType.WITHDRAWAL_CANCELLATION;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.auth.AuthenticatedPersonFixture;
import ee.tuleva.onboarding.conversion.UserConversionService;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.mandate.details.WithdrawalCancellationMandateDetails;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.MandateFixture;
import ee.tuleva.onboarding.mandate.builder.ConversionDecorator;
import ee.tuleva.onboarding.user.UserService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class WithdrawalCancellationMandateFactoryTest {

  @Mock private EpisService episService;

  @Mock private UserService userService;

  @Mock private UserConversionService conversionService;

  @Mock private ConversionDecorator conversionDecorator;

  @InjectMocks private WithdrawalCancellationMandateFactory withdrawalCancellationMandateFactory;

  @Test
  @DisplayName("delegates mandate creation to mandate factory")
  void testDelegateToMandateFactory() {
    var anUser = sampleUser().build();
    var aContactDetails = contactDetailsFixture();

    var anDto = MandateFixture.sampleMandateCreationDto(new WithdrawalCancellationMandateDetails());

    when(userService.getById(any())).thenReturn(anUser);
    when(conversionService.getConversion(any())).thenReturn(fullyConverted());
    when(episService.getContactDetails(any())).thenReturn(aContactDetails);

    Mandate genericMandate =
        withdrawalCancellationMandateFactory.createMandate(
            AuthenticatedPersonFixture.authenticatedPersonFromUser(anUser).build(), anDto);

    assertThat(genericMandate.getUser()).isEqualTo(anUser);
    assertThat(genericMandate.getAddress()).isEqualTo(aContactDetails.getAddress());
    assertThat(genericMandate.getFundTransferExchanges()).isEqualTo(List.of());
    verify(conversionDecorator, times(1)).addConversionMetadata(any(), any(), any(), any());

    assertThat(genericMandate.getDetails())
        .isInstanceOf(WithdrawalCancellationMandateDetails.class);
    assertThat(genericMandate.getPillar()).isEqualTo(2);
    assertThat(genericMandate.getGenericMandateDto().getMandateType())
        .isEqualTo(WITHDRAWAL_CANCELLATION);
  }

  @Test
  @DisplayName("supports WITHDRAWAL_CANCELLATION mandates")
  void testSupports() {
    assertThat(withdrawalCancellationMandateFactory.supports(WITHDRAWAL_CANCELLATION)).isTrue();
    assertThat(withdrawalCancellationMandateFactory.supports(EARLY_WITHDRAWAL_CANCELLATION))
        .isFalse();
  }
}
