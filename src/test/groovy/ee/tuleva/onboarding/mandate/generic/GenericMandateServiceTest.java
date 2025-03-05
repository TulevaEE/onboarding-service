package ee.tuleva.onboarding.mandate.generic;

import static ee.tuleva.onboarding.auth.UserFixture.*;
import static ee.tuleva.onboarding.mandate.MandateFixture.*;
import static ee.tuleva.onboarding.mandate.MandateType.*;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.auth.AuthenticatedPersonFixture;
import ee.tuleva.onboarding.epis.mandate.details.EarlyWithdrawalCancellationMandateDetails;
import ee.tuleva.onboarding.epis.mandate.details.MandateDetails;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.MandateFixture;
import ee.tuleva.onboarding.mandate.MandateService;
import ee.tuleva.onboarding.mandate.MandateType;
import ee.tuleva.onboarding.mandate.application.ApplicationType;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

public class GenericMandateServiceTest {

  private GenericMandateService genericMandateService;

  @Mock private MandateService mandateService;

  @Mock private UserService userService;

  @Mock private EarlyWithdrawalCancellationMandateFactory earlyWithdrawalCancellationMandateFactory;

  @Mock private WithdrawalCancellationMandateFactory withdrawalCancellationMandateFactory;

  private List<MandateFactory<?>> mandateFactories;

  @BeforeEach
  void setUp() {
    mandateService = mock(MandateService.class);
    userService = mock(UserService.class);

    earlyWithdrawalCancellationMandateFactory =
        mock(EarlyWithdrawalCancellationMandateFactory.class);
    withdrawalCancellationMandateFactory = mock(WithdrawalCancellationMandateFactory.class);
    mandateFactories =
        List.of(earlyWithdrawalCancellationMandateFactory, withdrawalCancellationMandateFactory);

    genericMandateService =
        new GenericMandateService(mandateFactories, mandateService, userService);
  }

  @Test
  @DisplayName("delegates mandate creation to mandate factory")
  void testDelegateToMandateFactory() {
    var anUser = sampleUser().build();
    var aMandate = sampleEarlyWithdrawalCancellationMandate();

    var anDto =
        MandateFixture.sampleMandateCreationDto(new EarlyWithdrawalCancellationMandateDetails());

    when(userService.getById(any())).thenReturn(anUser);
    when(mandateService.save(any(User.class), any(Mandate.class))).thenReturn(aMandate);

    when(earlyWithdrawalCancellationMandateFactory.supports(any())).thenCallRealMethod();
    when(withdrawalCancellationMandateFactory.supports(any())).thenCallRealMethod();

    when(earlyWithdrawalCancellationMandateFactory.createMandate(any(), any()))
        .thenReturn(aMandate);
    when(withdrawalCancellationMandateFactory.createMandate(any(), any())).thenReturn(aMandate);

    Mandate genericMandate =
        genericMandateService.createGenericMandate(
            AuthenticatedPersonFixture.authenticatedPersonFromUser(anUser).build(), anDto);

    assertThat(genericMandate).isEqualTo(aMandate);
  }

  @Test
  @DisplayName("throws when unsupported mandate type is passed")
  void testThrowOnMandateFactoryDelegation() {
    var anUser = sampleUser().build();
    var aMandate = sampleEarlyWithdrawalCancellationMandate();

    var anDto =
        MandateFixture.sampleMandateCreationDto(
            new MandateDetails(UNKNOWN) {
              @Override
              public ApplicationType getApplicationType() {
                return null;
              }

              @Override
              public MandateType getMandateType() {
                return UNKNOWN;
              }
            });

    when(userService.getById(any())).thenReturn(anUser);
    when(mandateService.save(any(User.class), any(Mandate.class))).thenReturn(aMandate);

    //
    when(earlyWithdrawalCancellationMandateFactory.supports(any())).thenCallRealMethod();
    when(withdrawalCancellationMandateFactory.supports(any())).thenCallRealMethod();

    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class,
            () ->
                genericMandateService.createGenericMandate(
                    AuthenticatedPersonFixture.authenticatedPersonFromUser(anUser).build(), anDto),
            "Expected createGenericMandate to throw, but didn't");

    assertThat(thrown.getMessage()).isEqualTo("Unsupported mandateType: UNKNOWN");
  }
}
