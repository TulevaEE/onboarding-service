package ee.tuleva.onboarding.secondpillarassets;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember;
import static ee.tuleva.onboarding.secondpillarassets.SecondPillarAssetsFixture.secondPillarAssetsFixture;
import static ee.tuleva.onboarding.secondpillarassets.SecondPillarAssetsFixture.secondPillarAssetsFixtureWithPik;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.account.CashFlowService;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.currency.Currency;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.cashflows.CashFlow;
import ee.tuleva.onboarding.epis.cashflows.CashFlowStatement;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SecondPillarAssetsServiceTest {

  @Mock private EpisService episService;
  @Mock private CashFlowService cashFlowService;
  @InjectMocks private SecondPillarAssetsService secondPillarAssetsService;

  private final AuthenticatedPerson person = sampleAuthenticatedPersonAndMember().build();

  @Test
  void returnsZeroTransferredToPik_whenPikFlagFalse() {
    given(episService.getSecondPillarAssets(person)).willReturn(secondPillarAssetsFixture());

    SecondPillarAssets enriched = secondPillarAssetsService.getSecondPillarAssets(person);

    assertThat(enriched.transferredToPik()).isEqualByComparingTo("0.00");
    verifyNoInteractions(cashFlowService);
  }

  @Test
  void sumsOutflowsToPik_whenPikFlagTrue() {
    given(episService.getSecondPillarAssets(person)).willReturn(secondPillarAssetsFixtureWithPik());
    given(cashFlowService.getCashFlowStatement(person))
        .willReturn(
            statementOf(
                pikTransferOut(new BigDecimal("-706.66")),
                regularContribution(new BigDecimal("706.66"))));

    SecondPillarAssets enriched = secondPillarAssetsService.getSecondPillarAssets(person);

    assertThat(enriched.transferredToPik()).isEqualByComparingTo("706.66");
  }

  @Test
  void returnsZero_whenInflowsFromPikMatchOrExceedOutflows() {
    given(episService.getSecondPillarAssets(person)).willReturn(secondPillarAssetsFixtureWithPik());
    given(cashFlowService.getCashFlowStatement(person))
        .willReturn(
            statementOf(
                pikTransferOut(new BigDecimal("-706.66")),
                pikTransferIn(new BigDecimal("800.00"))));

    SecondPillarAssets enriched = secondPillarAssetsService.getSecondPillarAssets(person);

    assertThat(enriched.transferredToPik()).isEqualByComparingTo("0.00");
  }

  @Test
  void preservesAllX84FieldsFromEpisService() {
    SecondPillarAssets source = secondPillarAssetsFixture();
    given(episService.getSecondPillarAssets(person)).willReturn(source);

    SecondPillarAssets enriched = secondPillarAssetsService.getSecondPillarAssets(person);

    assertThat(enriched)
        .usingRecursiveComparison()
        .ignoringFields("transferredToPik")
        .isEqualTo(source);
  }

  private static CashFlowStatement statementOf(CashFlow... cashFlows) {
    return CashFlowStatement.builder().transactions(List.of(cashFlows)).build();
  }

  private static CashFlow pikTransferOut(BigDecimal amount) {
    return CashFlow.builder()
        .time(Instant.parse("2025-11-13T00:00:00Z"))
        .amount(amount)
        .currency(Currency.EUR)
        .type(CashFlow.Type.TRANSFER_TO_PIK)
        .build();
  }

  private static CashFlow pikTransferIn(BigDecimal amount) {
    return CashFlow.builder()
        .time(Instant.parse("2026-02-01T00:00:00Z"))
        .amount(amount)
        .currency(Currency.EUR)
        .type(CashFlow.Type.TRANSFER_FROM_PIK)
        .build();
  }

  private static CashFlow regularContribution(BigDecimal amount) {
    return CashFlow.builder()
        .time(Instant.parse("2025-12-15T00:00:00Z"))
        .amount(amount)
        .currency(Currency.EUR)
        .type(CashFlow.Type.CONTRIBUTION_CASH_WORKPLACE)
        .build();
  }
}
