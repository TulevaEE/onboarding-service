package ee.tuleva.onboarding.savings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.savings.fund.nav.NavReportRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FundNavQueryServiceTest {

  @Mock NavReportRepository navReportRepository;

  @InjectMocks FundNavQueryService service;

  private static final LocalDate NAV_DATE = LocalDate.of(2026, 5, 7);

  @Test
  void findPublishedNavPerUnit_returnsTheOfficialNavPerUnit() {
    given(navReportRepository.findPublishedNavPerUnit(NAV_DATE, "TUK00", "NAV"))
        .willReturn(Optional.of(new BigDecimal("0.60985")));

    var result = service.findPublishedNavPerUnit("TUK00", NAV_DATE);

    assertThat(result).hasValue(new BigDecimal("0.60985"));
  }

  @Test
  void findPublishedNavPerUnit_returnsEmptyWhenNothingIsPublished() {
    given(navReportRepository.findPublishedNavPerUnit(NAV_DATE, "TUK00", "NAV"))
        .willReturn(Optional.empty());

    assertThat(service.findPublishedNavPerUnit("TUK00", NAV_DATE)).isEmpty();
  }

  @Test
  void findLatestNavPerUnit_readsTheNewestCalculationPublishedOrNot() {
    given(navReportRepository.findLatestNavPerUnit(NAV_DATE, "TUK00", "NAV"))
        .willReturn(Optional.of(new BigDecimal("0.61000")));

    var result = service.findLatestNavPerUnit("TUK00", NAV_DATE);

    assertThat(result).hasValue(new BigDecimal("0.61000"));
  }

  @Test
  void findLatestNavDateOnOrBefore_delegatesToRepositoryWithNavAccountType() {
    given(
            navReportRepository.findLatestNavDateByFundAndAccountTypeOnOrBefore(
                "TUK00", "NAV", NAV_DATE))
        .willReturn(Optional.of(NAV_DATE.minusDays(1)));

    var result = service.findLatestNavDateOnOrBefore("TUK00", NAV_DATE);

    assertThat(result).hasValue(NAV_DATE.minusDays(1));
  }
}
