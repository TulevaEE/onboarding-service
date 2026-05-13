package ee.tuleva.onboarding.savings.fund.nav;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

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
  void findNavPerUnit_returnsMarketPriceFromNavRow() {
    var navRow =
        NavReportRow.builder()
            .fundCode("TUK00")
            .navDate(NAV_DATE)
            .accountType("NAV")
            .accountName("Net Asset Value")
            .marketPrice(new BigDecimal("0.60985"))
            .build();
    given(navReportRepository.findFirstByFundCodeAndNavDateAndAccountType("TUK00", NAV_DATE, "NAV"))
        .willReturn(Optional.of(navRow));

    var result = service.findNavPerUnit("TUK00", NAV_DATE);

    assertThat(result).hasValue(new BigDecimal("0.60985"));
  }

  @Test
  void findNavPerUnit_returnsEmptyWhenNoRow() {
    given(navReportRepository.findFirstByFundCodeAndNavDateAndAccountType("TUK00", NAV_DATE, "NAV"))
        .willReturn(Optional.empty());

    assertThat(service.findNavPerUnit("TUK00", NAV_DATE)).isEmpty();
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
