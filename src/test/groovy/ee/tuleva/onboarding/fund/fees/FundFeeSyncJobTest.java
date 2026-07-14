package ee.tuleva.onboarding.fund.fees;

import static ee.tuleva.onboarding.fund.fees.FundFeeUpdater.FeeField.MANAGEMENT_FEE;
import static ee.tuleva.onboarding.fund.fees.FundFeeUpdater.FeeField.ONGOING_CHARGES;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FundFeeSyncJobTest {

  @Mock private PensionikeskusDailyStatisticsClient dailyStatisticsClient;
  @Mock private PensionikeskusFeeComparisonClient feeComparisonClient;
  @Mock private FundFeeUpdater fundFeeUpdater;

  private FundFeeSyncJob job;

  @BeforeEach
  void setUp() {
    job = new FundFeeSyncJob(dailyStatisticsClient, feeComparisonClient, fundFeeUpdater);
  }

  @Test
  void syncsBothFieldsForBothPillars() {
    var secondPillarOngoingCharges =
        List.of(new PensionikeskusFeeRow("A", new BigDecimal("0.0100")));
    var thirdPillarOngoingCharges =
        List.of(new PensionikeskusFeeRow("B", new BigDecimal("0.0055")));
    var secondPillarManagementFees =
        List.of(new PensionikeskusFeeRow("C", new BigDecimal("0.0060")));
    var thirdPillarManagementFees =
        List.of(new PensionikeskusFeeRow("D", new BigDecimal("0.0025")));
    given(dailyStatisticsClient.fetchOngoingCharges(2)).willReturn(secondPillarOngoingCharges);
    given(dailyStatisticsClient.fetchOngoingCharges(3)).willReturn(thirdPillarOngoingCharges);
    given(feeComparisonClient.fetchManagementFees(2)).willReturn(secondPillarManagementFees);
    given(feeComparisonClient.fetchManagementFees(3)).willReturn(thirdPillarManagementFees);

    job.syncFees();

    verify(fundFeeUpdater).update(2, secondPillarOngoingCharges, ONGOING_CHARGES);
    verify(fundFeeUpdater).update(3, thirdPillarOngoingCharges, ONGOING_CHARGES);
    verify(fundFeeUpdater).update(2, secondPillarManagementFees, MANAGEMENT_FEE);
    verify(fundFeeUpdater).update(3, thirdPillarManagementFees, MANAGEMENT_FEE);
  }

  @Test
  void continuesWhenOneSourceFails() {
    var thirdPillarOngoingCharges =
        List.of(new PensionikeskusFeeRow("B", new BigDecimal("0.0055")));
    var secondPillarManagementFees =
        List.of(new PensionikeskusFeeRow("C", new BigDecimal("0.0060")));
    var thirdPillarManagementFees =
        List.of(new PensionikeskusFeeRow("D", new BigDecimal("0.0025")));
    given(dailyStatisticsClient.fetchOngoingCharges(2)).willThrow(new RuntimeException("boom"));
    given(dailyStatisticsClient.fetchOngoingCharges(3)).willReturn(thirdPillarOngoingCharges);
    given(feeComparisonClient.fetchManagementFees(2)).willReturn(secondPillarManagementFees);
    given(feeComparisonClient.fetchManagementFees(3)).willReturn(thirdPillarManagementFees);

    job.syncFees();

    verify(fundFeeUpdater, never()).update(eq(2), any(), eq(ONGOING_CHARGES));
    verify(fundFeeUpdater).update(3, thirdPillarOngoingCharges, ONGOING_CHARGES);
    verify(fundFeeUpdater).update(2, secondPillarManagementFees, MANAGEMENT_FEE);
    verify(fundFeeUpdater).update(3, thirdPillarManagementFees, MANAGEMENT_FEE);
  }
}
