package ee.tuleva.onboarding.investment.fees.instrument;

import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import ee.tuleva.onboarding.investment.fees.rate.AccrualRates;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.investment.transaction.OpenTradeSource;
import ee.tuleva.onboarding.savings.FundNavQueryService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DataAccessResourceFailureException;

class InstrumentFeeAccrualServiceTest {

  private static final LocalDate SUNDAY = LocalDate.of(2026, 11, 22);

  private final AccrualRates accrualRates = Mockito.mock(AccrualRates.class);
  private final ProviderAgreementRepository providerAgreementRepository =
      Mockito.mock(ProviderAgreementRepository.class);
  private final HoldingReader holdingReader = Mockito.mock(HoldingReader.class);
  private final DayPriceReader dayPriceReader = Mockito.mock(DayPriceReader.class);
  private final InstrumentFeeAccrualRepository accrualRepository =
      Mockito.mock(InstrumentFeeAccrualRepository.class);
  private final InstrumentFeeAccrualService service =
      new InstrumentFeeAccrualService(
          accrualRates,
          providerAgreementRepository,
          holdingReader,
          dayPriceReader,
          accrualRepository,
          Clock.fixed(Instant.parse("2026-11-23T18:00:00Z"), UTC));

  @Test
  void oneFundFailingToStoreItsDayLeavesTheOtherFundsStoredAndNamesTheDayFailed() {
    given(accrualRates.on(SUNDAY)).willReturn(List.of());
    given(accrualRates.isinsWithoutAProviderAgreementOn(SUNDAY)).willReturn(new TreeSet<>());
    given(providerAgreementRepository.findVersionsValidOn(SUNDAY)).willReturn(Map.of());
    given(holdingReader.on(SUNDAY))
        .willReturn(
            new DayHoldings(
                SUNDAY,
                Mockito.mock(FundNavQueryService.class),
                Mockito.mock(FundPositionRepository.class),
                Mockito.mock(OpenTradeSource.class)));
    willThrow(new DataAccessResourceFailureException("synthetic outage"))
        .given(accrualRepository)
        .replaceTheFundsDay(eq(TUK00), eq(SUNDAY), any(), any());

    var run = service.accrueSince(SUNDAY);

    assertThat(run.accruedDays()).isEmpty();
    assertThat(run.failedDays()).containsExactly(SUNDAY);
    then(accrualRepository).should().replaceTheFundsDay(eq(TUK75), eq(SUNDAY), any(), any());
    then(accrualRepository).should().replaceTheFundsDay(eq(TUV100), eq(SUNDAY), any(), any());
    then(accrualRepository).should().replaceTheFundsDay(eq(TKF100), eq(SUNDAY), any(), any());
  }
}
