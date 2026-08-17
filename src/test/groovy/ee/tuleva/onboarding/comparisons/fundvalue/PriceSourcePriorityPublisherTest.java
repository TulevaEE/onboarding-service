package ee.tuleva.onboarding.comparisons.fundvalue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@ExtendWith(MockitoExtension.class)
class PriceSourcePriorityPublisherTest {

  @Mock private NamedParameterJdbcTemplate jdbcTemplate;

  @InjectMocks private PriceSourcePriorityPublisher publisher;

  @Test
  void publish_writesEveryPriceFeedInPriorityProviderOrder() {
    publisher.publish();

    ArgumentCaptor<Map<String, ?>> params = ArgumentCaptor.captor();
    verify(jdbcTemplate, org.mockito.Mockito.atLeastOnce()).update(anyString(), params.capture());

    List<String> written = new ArrayList<>();
    for (Map<String, ?> p : params.getAllValues()) {
      if (p.containsKey("priceSource")) {
        assertThat(p.get("rank")).isEqualTo(written.size() + 1); // ranks are 1..n, in order
        written.add(String.valueOf(p.get("priceSource")));
      }
    }

    List<String> expected =
        PriorityPriceProvider.priceFeeds().stream().map(feed -> feed.source().name()).toList();

    assertThat(written).isEqualTo(expected);
  }

  @Test
  void publish_clearsTheTableFirstSoARemovedSourceCannotLinger() {
    publisher.publish();

    verify(jdbcTemplate).update(eq("DELETE FROM price_source_priority"), eq(Map.of()));
  }

  @Test
  void publishedOrderMatchesTheOrderResolveActuallyApplies() {
    // The published rank must be the same tie-break PriorityPriceProvider uses when two sources
    // carry the same date, otherwise a SQL consumer reading this table would disagree with us.
    List<String> feedOrder =
        PriorityPriceProvider.priceFeeds().stream().map(feed -> feed.source().name()).toList();

    assertThat(feedOrder).startsWith("BLACKROCK", "MORNINGSTAR", "EODHD");
    assertThat(feedOrder)
        .containsExactlyInAnyOrderElementsOf(
            java.util.Arrays.stream(PriceSource.values()).map(Enum::name).toList());
  }
}
