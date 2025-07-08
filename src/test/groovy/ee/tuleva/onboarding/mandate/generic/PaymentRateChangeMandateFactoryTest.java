package ee.tuleva.onboarding.mandate.generic;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.conversion.ConversionResponseFixture.*;
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture;
import static ee.tuleva.onboarding.epis.mandate.details.PaymentRateChangeMandateDetails.PaymentRate.SIX;
import static ee.tuleva.onboarding.mandate.MandateFixture.*;
import static ee.tuleva.onboarding.mandate.MandateType.*;
import static ee.tuleva.onboarding.pillar.Pillar.SECOND;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.auth.AuthenticatedPersonFixture;
import ee.tuleva.onboarding.conversion.UserConversionService;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.mandate.details.PaymentRateChangeMandateDetails;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.builder.ConversionDecorator;
import ee.tuleva.onboarding.user.UserService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PaymentRateChangeMandateFactoryTest {

  @Mock private EpisService episService;

  @Mock private UserService userService;

  @Mock private UserConversionService conversionService;

  @Mock private ConversionDecorator conversionDecorator;

  @InjectMocks private PaymentRateChangeMandateFactory paymentRateChangeMandateFactory;

  @Test
  @DisplayName("delegates mandate creation to mandate factory")
  void testDelegateToMandateFactory() {
    var anUser = sampleUser().build();
    var aContactDetails = contactDetailsFixture();

    var anDto = sampleMandateCreationDto(new PaymentRateChangeMandateDetails(SIX));

    when(userService.getById(any())).thenReturn(Optional.of(anUser));
    when(conversionService.getConversion(any())).thenReturn(fullyConverted());
    when(episService.getContactDetails(any())).thenReturn(aContactDetails);

    Mandate genericMandate =
        paymentRateChangeMandateFactory.createMandate(
            AuthenticatedPersonFixture.authenticatedPersonFromUser(anUser).build(), anDto);

    assertThat(genericMandate.getUser()).isEqualTo(anUser);
    assertThat(genericMandate.getAddress()).isEqualTo(aContactDetails.getAddress());
    assertThat(genericMandate.getFundTransferExchanges()).isEqualTo(List.of());
    verify(conversionDecorator, times(1)).addConversionMetadata(any(), any(), any(), any());

    assertThat(genericMandate.getDetails()).isInstanceOf(PaymentRateChangeMandateDetails.class);
    assertThat(((PaymentRateChangeMandateDetails) genericMandate.getDetails()).getPaymentRate())
        .isEqualTo(SIX);
    assertThat(genericMandate.getPillar()).isEqualTo(SECOND.toInt());

    assertThat(genericMandate.getGenericMandateDto().getMandateType())
        .isEqualTo(PAYMENT_RATE_CHANGE);
  }

  @Test
  @DisplayName("supports PAYMENT_RATE_CHANGE mandates")
  void testSupports() {
    assertThat(paymentRateChangeMandateFactory.supports(PAYMENT_RATE_CHANGE)).isTrue();
    assertThat(paymentRateChangeMandateFactory.supports(WITHDRAWAL_CANCELLATION)).isFalse();
  }
}
