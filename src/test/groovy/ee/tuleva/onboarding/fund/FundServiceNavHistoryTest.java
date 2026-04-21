package ee.tuleva.onboarding.fund;

import static ee.tuleva.onboarding.comparisons.fundvalue.FundValueFixture.aFundValue;
import static ee.tuleva.onboarding.fund.FundFixture.additionalSavingsFund;
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleFunds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
import ee.tuleva.onboarding.fund.statistics.PensionFundStatisticsService;
import ee.tuleva.onboarding.ledger.LedgerService;
import ee.tuleva.onboarding.locale.LocaleService;
import ee.tuleva.onboarding.savings.fund.SavingsFundConfiguration;
import ee.tuleva.onboarding.savings.fund.nav.FundNavProvider;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class FundServiceNavHistoryTest {

  private static final String TKF_ISIN = "EE0000003283";

  @Mock private FundRepository fundRepository;
  @Mock private PensionFundStatisticsService pensionFundStatisticsService;
  @Mock private FundValueRepository fundValueRepository;
  @Mock private LocaleService localeService;
  @Mock private LedgerService ledgerService;
  @Mock private SavingsFundConfiguration savingsFundConfiguration;
  @Mock private FundNavProvider fundNavProvider;

  @InjectMocks private FundService fundService;

  @Test
  void getNavHistory_returnsMappedFundValues() {
    LocalDate start = LocalDate.of(2026, 2, 2);
    LocalDate end = LocalDate.of(2026, 4, 14);
    given(savingsFundConfiguration.getIsin()).willReturn(TKF_ISIN);
    given(fundRepository.findByIsin(TKF_ISIN)).willReturn(additionalSavingsFund());
    given(fundNavProvider.safeMaxNavDate()).willReturn(LocalDate.of(2099, 1, 1));
    given(fundValueRepository.findValuesBetweenDates(TKF_ISIN, start, end))
        .willReturn(
            List.of(
                aFundValue(TKF_ISIN, LocalDate.of(2026, 2, 3), new BigDecimal("1.0000")),
                aFundValue(TKF_ISIN, LocalDate.of(2026, 2, 4), new BigDecimal("1.0012"))));

    List<NavValueResponse> result = fundService.getNavHistory(TKF_ISIN, start, end);

    assertThat(result)
        .containsExactly(
            new NavValueResponse(LocalDate.of(2026, 2, 3), new BigDecimal("1.0000")),
            new NavValueResponse(LocalDate.of(2026, 2, 4), new BigDecimal("1.0012")));
  }

  @Test
  void getNavHistory_defaultsNullDatesToEpochAndSafeMaxDateForSavingsFund() {
    LocalDate safeMaxDate = LocalDate.of(2026, 4, 19);
    given(savingsFundConfiguration.getIsin()).willReturn(TKF_ISIN);
    given(fundRepository.findByIsin(TKF_ISIN)).willReturn(additionalSavingsFund());
    given(fundNavProvider.safeMaxNavDate()).willReturn(safeMaxDate);
    given(fundValueRepository.findValuesBetweenDates(TKF_ISIN, LocalDate.EPOCH, safeMaxDate))
        .willReturn(List.of());

    List<NavValueResponse> result = fundService.getNavHistory(TKF_ISIN, null, null);

    assertThat(result).isEmpty();
  }

  @Test
  void getNavHistory_defaultsNullDatesToEpochAndFarFutureForNonSavingsFund() {
    Fund nonSavingsFund = firstTulevaNonSavingsFund();
    String isin = nonSavingsFund.getIsin();
    given(savingsFundConfiguration.getIsin()).willReturn(TKF_ISIN);
    given(fundRepository.findByIsin(isin)).willReturn(nonSavingsFund);
    given(
            fundValueRepository.findValuesBetweenDates(
                isin, LocalDate.EPOCH, LocalDate.of(9999, 12, 31)))
        .willReturn(List.of());

    List<NavValueResponse> result = fundService.getNavHistory(isin, null, null);

    assertThat(result).isEmpty();
    then(fundValueRepository)
        .should()
        .findValuesBetweenDates(isin, LocalDate.EPOCH, LocalDate.of(9999, 12, 31));
  }

  @Test
  void getNavHistory_capsSavingsFundEndDateToSafeMaxDate() {
    LocalDate safeMaxDate = LocalDate.of(2026, 4, 19);
    LocalDate requestedEnd = LocalDate.of(2026, 4, 20);
    given(savingsFundConfiguration.getIsin()).willReturn(TKF_ISIN);
    given(fundRepository.findByIsin(TKF_ISIN)).willReturn(additionalSavingsFund());
    given(fundNavProvider.safeMaxNavDate()).willReturn(safeMaxDate);
    given(fundValueRepository.findValuesBetweenDates(TKF_ISIN, LocalDate.EPOCH, safeMaxDate))
        .willReturn(List.of());

    fundService.getNavHistory(TKF_ISIN, null, requestedEnd);

    then(fundValueRepository)
        .should()
        .findValuesBetweenDates(TKF_ISIN, LocalDate.EPOCH, safeMaxDate);
    then(fundValueRepository)
        .should(never())
        .findValuesBetweenDates(TKF_ISIN, LocalDate.EPOCH, requestedEnd);
  }

  @Test
  void getNavHistory_doesNotCapNonSavingsFundEndDate() {
    Fund nonSavingsFund = firstTulevaNonSavingsFund();
    String isin = nonSavingsFund.getIsin();
    LocalDate requestedEnd = LocalDate.of(2026, 4, 20);
    given(savingsFundConfiguration.getIsin()).willReturn(TKF_ISIN);
    given(fundRepository.findByIsin(isin)).willReturn(nonSavingsFund);
    given(fundValueRepository.findValuesBetweenDates(isin, LocalDate.EPOCH, requestedEnd))
        .willReturn(List.of());

    fundService.getNavHistory(isin, null, requestedEnd);

    then(fundNavProvider).should(never()).safeMaxNavDate();
    then(fundValueRepository).should().findValuesBetweenDates(isin, LocalDate.EPOCH, requestedEnd);
  }

  @Test
  void getNavHistory_returnsEmptyListWhenStartIsAfterCappedEnd() {
    LocalDate safeMaxDate = LocalDate.of(2026, 4, 19);
    LocalDate requestedStart = LocalDate.of(2026, 4, 20);
    LocalDate requestedEnd = LocalDate.of(2026, 4, 25);
    given(savingsFundConfiguration.getIsin()).willReturn(TKF_ISIN);
    given(fundRepository.findByIsin(TKF_ISIN)).willReturn(additionalSavingsFund());
    given(fundNavProvider.safeMaxNavDate()).willReturn(safeMaxDate);

    List<NavValueResponse> result =
        fundService.getNavHistory(TKF_ISIN, requestedStart, requestedEnd);

    assertThat(result).isEmpty();
    then(fundValueRepository).shouldHaveNoInteractions();
  }

  @Test
  void getNavHistory_usesDbFundIsinNotRequestIsinForQueryAndCapCheck() {
    String requestIsin = "ee0000003283";
    LocalDate safeMaxDate = LocalDate.of(2026, 4, 19);
    given(savingsFundConfiguration.getIsin()).willReturn(TKF_ISIN);
    given(fundRepository.findByIsin(requestIsin)).willReturn(additionalSavingsFund());
    given(fundNavProvider.safeMaxNavDate()).willReturn(safeMaxDate);
    given(fundValueRepository.findValuesBetweenDates(TKF_ISIN, LocalDate.EPOCH, safeMaxDate))
        .willReturn(List.of());

    fundService.getNavHistory(requestIsin, null, LocalDate.of(2026, 4, 25));

    then(fundValueRepository)
        .should()
        .findValuesBetweenDates(TKF_ISIN, LocalDate.EPOCH, safeMaxDate);
  }

  @Test
  void getNavHistory_throwsNotFoundForUnknownIsin() {
    given(fundRepository.findByIsin("UNKNOWN")).willReturn(null);

    assertThatThrownBy(() -> fundService.getNavHistory("UNKNOWN", null, null))
        .asInstanceOf(InstanceOfAssertFactories.throwable(ResponseStatusException.class))
        .extracting(ResponseStatusException::getStatusCode)
        .isEqualTo(NOT_FOUND);
  }

  @Test
  void getNavHistoryCsv_returnsSemicolonDelimitedCsvWithBom() {
    LocalDate start = LocalDate.of(2026, 2, 2);
    LocalDate end = LocalDate.of(2026, 4, 14);
    given(savingsFundConfiguration.getIsin()).willReturn(TKF_ISIN);
    given(fundRepository.findByIsin(TKF_ISIN)).willReturn(additionalSavingsFund());
    given(fundNavProvider.safeMaxNavDate()).willReturn(LocalDate.of(2099, 1, 1));
    given(fundValueRepository.findValuesBetweenDates(TKF_ISIN, start, end))
        .willReturn(
            List.of(
                aFundValue(TKF_ISIN, LocalDate.of(2026, 2, 3), new BigDecimal("1.0000")),
                aFundValue(TKF_ISIN, LocalDate.of(2026, 2, 4), new BigDecimal("1.0012"))));

    byte[] bytes = fundService.getNavHistoryCsv(TKF_ISIN, start, end);

    String csv = new String(bytes, StandardCharsets.UTF_8);
    assertThat(csv)
        .isEqualTo("\uFEFFKuupäev;NAV (EUR)\r\n03.02.2026;1.0000\r\n04.02.2026;1.0012\r\n");
  }

  private Fund firstTulevaNonSavingsFund() {
    return sampleFunds().stream()
        .filter(f -> f.getFundManager().getName().equals("Tuleva"))
        .findFirst()
        .get();
  }
}
