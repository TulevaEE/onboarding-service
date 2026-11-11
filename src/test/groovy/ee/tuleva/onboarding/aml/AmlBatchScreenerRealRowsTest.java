package ee.tuleva.onboarding.aml;

import static ee.tuleva.onboarding.aml.AmlCheckType.POLITICALLY_EXPOSED_PERSON_AUTO;
import static ee.tuleva.onboarding.aml.AmlCheckType.SANCTION;
import static ee.tuleva.onboarding.analytics.transaction.unitowner.UnitOwnerFixture.entityBuilder;
import static ee.tuleva.onboarding.analytics.transaction.unitowner.UnitOwnerFixture.unitOwnerBalanceEmbeddableBuilder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.aml.sanctions.MatchResponse;
import ee.tuleva.onboarding.aml.sanctions.PepAndSanctionCheckService;
import ee.tuleva.onboarding.analytics.ThirdPillarAnalytics;
import ee.tuleva.onboarding.analytics.ThirdPillarHolder;
import ee.tuleva.onboarding.analytics.transaction.thirdpillar.AnalyticsThirdPillarTransactionRepository;
import ee.tuleva.onboarding.analytics.transaction.unitowner.UnitOwnerRepository;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.conversion.UserConversionService;
import ee.tuleva.onboarding.country.Countries;
import ee.tuleva.onboarding.kyc.KycCountryService;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.user.UserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.json.JsonMapper;

@DataJpaTest
class AmlBatchScreenerRealRowsTest {

  private static final String HOLDER = "38001085718";
  private static final ThirdPillarHolder EXPECTED_HOLDER =
      new ThirdPillarHolder(HOLDER, "Mari", "Maasikas", "EE");
  private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

  @Autowired private UnitOwnerRepository unitOwnerRepository;
  @Autowired private AmlCheckRepository amlCheckRepository;

  private final PepAndSanctionCheckService pepAndSanctionCheckService =
      mock(PepAndSanctionCheckService.class);
  private AmlBatchScreener amlBatchScreener;

  @BeforeEach
  void setUp() {
    ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    AmlService amlService =
        new AmlService(amlCheckRepository, eventPublisher, mock(UserConversionService.class));
    SanctionAndPepScreener sanctionAndPepScreener =
        new SanctionAndPepScreener(
            amlCheckRepository,
            pepAndSanctionCheckService,
            mock(KycCountryService.class),
            JSON_MAPPER,
            new SimpleMeterRegistry(),
            amlService);
    amlBatchScreener =
        new AmlBatchScreener(
            eventPublisher,
            new ThirdPillarAnalytics(
                mock(AnalyticsThirdPillarTransactionRepository.class), unitOwnerRepository),
            mock(SavingsFundCustomers.class),
            mock(UserRepository.class),
            mock(OperationsNotificationService.class),
            sanctionAndPepScreener,
            amlCheckRepository);
    given(pepAndSanctionCheckService.match(any(Person.class), anySet()))
        .willReturn(
            new MatchResponse(JSON_MAPPER.createArrayNode(), JSON_MAPPER.createObjectNode()));
    unitOwnerRepository.save(
        entityBuilder(HOLDER, LocalDate.of(2026, 11, 5), LocalDateTime.of(2026, 11, 5, 4, 30))
            .balances(List.of(unitOwnerBalanceEmbeddableBuilder("TUV100", "III").build()))
            .build());
  }

  @Test
  void screensNobodyWhenTheHolderHasSanctionAndPepChecksThisYear() {
    saveCheck(SANCTION);
    saveCheck(POLITICALLY_EXPOSED_PERSON_AUTO);

    amlBatchScreener.runAmlChecksOnThirdPillarHoldersWithoutRecentScreening();

    verify(pepAndSanctionCheckService, never()).match(any(Person.class), anySet());
  }

  @Test
  void screensTheHolderWithoutChecksThisYear() {
    amlBatchScreener.runAmlChecksOnThirdPillarHoldersWithoutRecentScreening();

    verify(pepAndSanctionCheckService).match(EXPECTED_HOLDER, Countries.of("EE"));
  }

  @Test
  void screensTheHolderWhosePepCheckIsMissing() {
    saveCheck(SANCTION);

    amlBatchScreener.runAmlChecksOnThirdPillarHoldersWithoutRecentScreening();

    verify(pepAndSanctionCheckService).match(EXPECTED_HOLDER, Countries.of("EE"));
  }

  private void saveCheck(AmlCheckType type) {
    amlCheckRepository.save(
        AmlCheck.builder().personalCode(HOLDER).type(type).success(true).build());
  }
}
