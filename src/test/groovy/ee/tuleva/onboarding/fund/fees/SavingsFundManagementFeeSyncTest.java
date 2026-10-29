package ee.tuleva.onboarding.fund.fees;

import static ee.tuleva.onboarding.fund.FundFixture.additionalSavingsFund;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.fund.FundRepository;
import ee.tuleva.onboarding.fund.ManagementFeeRates;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SavingsFundManagementFeeSyncTest {

  private static final LocalDate TODAY = LocalDate.parse("2026-06-04");
  private static final ManagementFeeRates RATE_IN_FORCE =
      (fund, date) -> Optional.of(new BigDecimal("0.00450000"));

  @Mock private FundRepository fundRepository;

  @Test
  void aFundTableRateEqualToTheRateInForceAtAnotherScaleIsNotRewritten() {
    given(fundRepository.findUncachedByIsin(TKF100.getIsin()))
        .willReturn(Optional.of(savingsFundShowing("0.0045000")));

    syncWith(RATE_IN_FORCE);

    verify(fundRepository, never()).save(any());
  }

  @Test
  void aSavingsFundMissingFromTheFundTableIsSkippedWithoutFailingTheJob() {
    given(fundRepository.findUncachedByIsin(TKF100.getIsin())).willReturn(Optional.empty());

    assertThatCode(() -> syncWith(RATE_IN_FORCE)).doesNotThrowAnyException();
    verify(fundRepository, never()).save(any());
  }

  @Test
  void aSavingsFundThatFailsToSyncIsLoggedWithoutStoppingTheRun() {
    given(fundRepository.findUncachedByIsin(TKF100.getIsin()))
        .willThrow(new IllegalStateException("database unavailable"));

    assertThatCode(() -> syncWith(RATE_IN_FORCE)).doesNotThrowAnyException();
    verify(fundRepository, never()).save(any());
  }

  @Test
  void anEightDecimalRateIsWrittenOnceAtTheFundTablesScaleAndNotRewrittenByTheNextRun() {
    given(fundRepository.findUncachedByIsin(TKF100.getIsin()))
        .willReturn(
            Optional.of(savingsFundShowing("0.0040000")),
            Optional.of(savingsFundShowing("0.0045001")));
    ManagementFeeRates eightDecimalRate = (fund, date) -> Optional.of(new BigDecimal("0.00450005"));

    syncWith(eightDecimalRate);
    syncWith(eightDecimalRate);

    verify(fundRepository).save(savingsFundShowing("0.0045001"));
    verify(fundRepository, times(1)).save(any());
  }

  private void syncWith(ManagementFeeRates rates) {
    new SavingsFundManagementFeeSync(rates, fundRepository, new FundFeeUpdater(fundRepository))
        .syncToTheRatesInForceOn(TODAY);
  }

  private static Fund savingsFundShowing(String managementFeeRate) {
    var savingsFund = additionalSavingsFund();
    savingsFund.setManagementFeeRate(new BigDecimal(managementFeeRate));
    return savingsFund;
  }
}
