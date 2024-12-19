package ee.tuleva.onboarding.mandate.generic;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.conversion.ConversionResponseFixture.fullyConverted;
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture;
import static ee.tuleva.onboarding.fund.FundFixture.lhv3rdPillarFund;
import static ee.tuleva.onboarding.mandate.MandateType.*;
import static ee.tuleva.onboarding.pillar.Pillar.*;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.auth.AuthenticatedPersonFixture;
import ee.tuleva.onboarding.conversion.UserConversionService;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.mandate.details.TransferCancellationMandateDetails;
import ee.tuleva.onboarding.fund.FundRepository;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.MandateFixture;
import ee.tuleva.onboarding.mandate.builder.ConversionDecorator;
import ee.tuleva.onboarding.user.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class TransferCancellationMandateFactoryTest {

  @Mock private FundRepository fundRepository;

  @Mock private EpisService episService;

  @Mock private UserService userService;

  @Mock private UserConversionService conversionService;

  @Mock private ConversionDecorator conversionDecorator;

  @InjectMocks private TransferCancellationMandateFactory transferCancellationMandateFactory;

  @Test
  @DisplayName("delegates mandate creation to mandate factory")
  void testDelegateToMandateFactory() {
    var anUser = sampleUser().build();
    var aContactDetails = contactDetailsFixture();
    var aFund = lhv3rdPillarFund();

    var testIsin = aFund.getIsin();
    var testPillar = THIRD;

    var anDto =
        MandateFixture.sampleMandateCreationDto(
            new TransferCancellationMandateDetails(testIsin, testPillar));

    when(userService.getById(any())).thenReturn(anUser);
    when(conversionService.getConversion(any())).thenReturn(fullyConverted());
    when(episService.getContactDetails(any())).thenReturn(aContactDetails);
    when(fundRepository.findByIsin(eq(testIsin))).thenReturn(aFund);

    Mandate genericMandate =
        transferCancellationMandateFactory.createMandate(
            AuthenticatedPersonFixture.authenticatedPersonFromUser(anUser).build(), anDto);

    assertThat(genericMandate.getUser()).isEqualTo(anUser);
    assertThat(genericMandate.getAddress()).isEqualTo(aContactDetails.getAddress());
    verify(conversionDecorator, times(1)).addConversionMetadata(any(), any(), any(), any());

    assertThat(genericMandate.getDetails()).isInstanceOf(TransferCancellationMandateDetails.class);
    assertThat(genericMandate.getGenericMandateDto().getMandateType())
        .isEqualTo(TRANSFER_CANCELLATION);

    assertThat(genericMandate.getPillar()).isEqualTo(testPillar.toInt());
    assertThat(genericMandate.getFundTransferExchanges().size()).isEqualTo(1);

    var fundTransferExchange = genericMandate.getFundTransferExchanges().getFirst();

    assertThat(fundTransferExchange.getSourceFundIsin()).isEqualTo(testIsin);
    assertThat(fundTransferExchange.getTargetFundIsin()).isNull();
    assertThat(fundTransferExchange.getAmount()).isNull();
    assertThat(fundTransferExchange.getMandate()).isEqualTo(genericMandate);
  }

  @Test
  @DisplayName("supports TRANSFER_CANCELLATION mandates")
  void testSupports() {
    assertThat(transferCancellationMandateFactory.supports(TRANSFER_CANCELLATION)).isTrue();
    assertThat(transferCancellationMandateFactory.supports(EARLY_WITHDRAWAL_CANCELLATION))
        .isFalse();
  }
}
