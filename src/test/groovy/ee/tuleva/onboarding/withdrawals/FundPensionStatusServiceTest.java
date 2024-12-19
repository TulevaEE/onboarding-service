package ee.tuleva.onboarding.withdrawals;

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson;
import static ee.tuleva.onboarding.auth.PersonFixture.sampleRetirementAgePerson;
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture;
import static ee.tuleva.onboarding.pillar.Pillar.SECOND;
import static ee.tuleva.onboarding.pillar.Pillar.THIRD;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.withdrawals.FundPensionStatusDto;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FundPensionStatusServiceTest {

  @Mock private EpisService episService;

  @InjectMocks private FundPensionStatusService fundPensionStatusService;

  @Test
  @DisplayName("gets fund pension status for those over 55")
  void shouldGetFundPensionOver55() {
    var aPerson = sampleRetirementAgePerson;
    var aContactDetails = contactDetailsFixture();
    aContactDetails.setPersonalCode(aPerson.getPersonalCode());

    var secondPillarFundPensions =
        List.of(
            new FundPensionStatusDto.FundPensionDto(
                Instant.parse("2019-10-01T12:13:27.141Z"), null, 20, true));
    var thirdPillarFundPensions =
        List.of(
            new FundPensionStatusDto.FundPensionDto(
                Instant.parse("2019-10-01T12:13:27.141Z"),
                Instant.parse("2023-10-01T12:13:27.141Z"),
                20,
                false));
    var fundPensionStatus =
        new FundPensionStatusDto(secondPillarFundPensions, thirdPillarFundPensions);

    when(episService.getFundPensionStatus(any())).thenReturn(fundPensionStatus);

    var result = fundPensionStatusService.getFundPensionStatus(aPerson);

    assertThat(result.fundPensions().size()).isEqualTo(2);
    assertThat(
            result.fundPensions().stream()
                .filter(fundPension -> fundPension.pillar() == SECOND)
                .count())
        .isEqualTo(1);
    assertThat(
            result.fundPensions().stream()
                .filter(fundPension -> fundPension.pillar() == THIRD)
                .count())
        .isEqualTo(1);
  }

  @Test
  @DisplayName("gets empty fund pension status for those over 55")
  void shouldGetFundPensionUnder55() {
    var aPerson = samplePerson;
    var aContactDetails = contactDetailsFixture();
    aContactDetails.setPersonalCode(aPerson.getPersonalCode());

    var result = fundPensionStatusService.getFundPensionStatus(aPerson);

    assertThat(result.fundPensions().size()).isEqualTo(0);
    verify(episService, never()).getFundPensionStatus(any());
  }
}
