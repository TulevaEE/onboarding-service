package ee.tuleva.onboarding.epis.transaction;

import static ee.tuleva.onboarding.config.JsonMapperFixture.jsonMapper;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class Pillar2DetailsDtoTest {

  @Test
  void readsTheLatestRejoinApplicationFromTheEpisServiceJson() {
    Pillar2DetailsDto details =
        jsonMapper()
            .readValue(
                """
                {"mmteDate": "2026-10-01", "mmteStatus": "T", "mateDate": "2026-11-02", "mateStatus": "A"}
                """,
                Pillar2DetailsDto.class);

    assertThat(details)
        .isEqualTo(
            Pillar2DetailsDto.builder()
                .mmteDate(LocalDate.of(2026, 10, 1))
                .mmteStatus("T")
                .mateDate(LocalDate.of(2026, 11, 2))
                .mateStatus("A")
                .build());
  }
}
