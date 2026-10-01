package ee.tuleva.onboarding.savings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.analytics.SaverId;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.fund.SavingFundStandingOrders;
import java.time.LocalDate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SavingsFundContributionsAdapterTest {

  @Mock SavingFundStandingOrders standingOrders;
  @InjectMocks SavingsFundContributionsAdapter adapter;

  @ParameterizedTest
  @CsvSource({"PERSON,PERSON", "LEGAL_ENTITY,LEGAL_ENTITY"})
  void delegatesWithMappedPartyId(SaverId.Type saverType, PartyId.Type partyType) {
    var from = LocalDate.of(2024, 1, 1);
    var saver = new SaverId(saverType, "12345678");
    given(standingOrders.countMonthsSince(new PartyId(partyType, "12345678"), from)).willReturn(3);

    assertThat(adapter.countStandingOrderMonthsSince(saver, from)).isEqualTo(3);
  }
}
