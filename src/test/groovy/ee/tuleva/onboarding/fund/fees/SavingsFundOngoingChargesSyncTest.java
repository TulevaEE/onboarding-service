package ee.tuleva.onboarding.fund.fees;

import static ee.tuleva.onboarding.fund.FundFixture.additionalSavingsFund;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.fund.FundRepository;
import ee.tuleva.onboarding.fund.OngoingChargesFigures;
import ee.tuleva.onboarding.fund.PublishedFigure;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SavingsFundOngoingChargesSyncTest {

  private static final LocalDate TODAY = LocalDate.parse("2026-10-29");
  private static final OngoingChargesFigures KID_IN_FORCE =
      (fund, date) -> Optional.of(new PublishedFigure(new BigDecimal("0.0028"), 2));

  @Mock private FundRepository fundRepository;

  @Test
  void aFundTableFigureEqualToTheKidsFigureAtAnotherScaleIsNotRewritten() {
    given(fundRepository.findUncachedByIsin(TKF100.getIsin()))
        .willReturn(Optional.of(savingsFundShowing("0.00280000")));

    syncWith(KID_IN_FORCE);

    verify(fundRepository, never()).save(any());
  }

  @Test
  void aDifferentFundTableFigureIsReplacedWithTheKidsFigureAtTheFundTablesScale() {
    given(fundRepository.findUncachedByIsin(TKF100.getIsin()))
        .willReturn(Optional.of(savingsFundShowing("0.00290000")));

    syncWith(KID_IN_FORCE);

    verify(fundRepository).save(savingsFundShowing("0.00280000"));
  }

  @Test
  void aSavingsFundMissingFromTheFundTableIsSkippedWithoutFailingTheJob() {
    given(fundRepository.findUncachedByIsin(TKF100.getIsin())).willReturn(Optional.empty());

    assertThatCode(() -> syncWith(KID_IN_FORCE)).doesNotThrowAnyException();
    verify(fundRepository, never()).save(any());
  }

  @Test
  void aSavingsFundThatFailsToSyncIsLoggedWithoutFailingTheJob() {
    given(fundRepository.findUncachedByIsin(TKF100.getIsin()))
        .willThrow(new IllegalStateException("database unavailable"));

    assertThatCode(() -> syncWith(KID_IN_FORCE)).doesNotThrowAnyException();
    verify(fundRepository, never()).save(any());
  }

  @Test
  void withNoKidInForceTheFundTableFigureIsLeftAlone() {
    given(fundRepository.findUncachedByIsin(TKF100.getIsin()))
        .willReturn(Optional.of(savingsFundShowing("0.00290000")));

    syncWith((fund, date) -> Optional.empty());

    verify(fundRepository, never()).save(any());
  }

  private void syncWith(OngoingChargesFigures figures) {
    new SavingsFundOngoingChargesSync(figures, fundRepository, new FundFeeUpdater(fundRepository))
        .syncToTheKidsInForceOn(TODAY);
  }

  private static Fund savingsFundShowing(String ongoingChargesFigure) {
    var savingsFund = additionalSavingsFund();
    savingsFund.setOngoingChargesFigure(new BigDecimal(ongoingChargesFigure));
    return savingsFund;
  }
}
