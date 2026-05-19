package ee.tuleva.onboarding.comparisons;

import static ee.tuleva.onboarding.comparisons.IndexValueController.MAX_KEYS;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(IndexValueController.class)
@WithMockUser
class IndexValueControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private FundValueRepository fundValueRepository;

  @Test
  void getIndexValues_returnsCsvWithLatestValues() throws Exception {
    var values =
        List.of(
            new FundValue(
                "SGAS.XETRA",
                LocalDate.of(2026, 5, 8),
                new BigDecimal("13.028"),
                "EODHD",
                Instant.parse("2026-05-08T18:00:00Z")),
            new FundValue(
                "IE00BFNM3G45.XETR",
                LocalDate.of(2026, 5, 8),
                new BigDecimal("13.028"),
                "DEUTSCHE_BOERSE",
                Instant.parse("2026-05-08T18:00:00Z")));
    given(fundValueRepository.findLatestValuesByKeys(List.of("SGAS.XETRA", "IE00BFNM3G45.XETR")))
        .willReturn(values);

    mockMvc
        .perform(
            get("/v1/index-values")
                .param("keys", "SGAS.XETRA,IE00BFNM3G45.XETR")
                .param("format", "csv"))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(
            content()
                .string(
                    "key,date,value,provider\r\n"
                        + "SGAS.XETRA,2026-05-08,13.028,EODHD\r\n"
                        + "IE00BFNM3G45.XETR,2026-05-08,13.028,DEUTSCHE_BOERSE\r\n"));
  }

  @Test
  void getIndexValues_withDateRange_returnsCsvForRange() throws Exception {
    var values =
        List.of(
            new FundValue(
                "SGAS.XETRA",
                LocalDate.of(2026, 5, 7),
                new BigDecimal("12.990"),
                "EODHD",
                Instant.parse("2026-05-07T18:00:00Z")),
            new FundValue(
                "SGAS.XETRA",
                LocalDate.of(2026, 5, 8),
                new BigDecimal("13.028"),
                "EODHD",
                Instant.parse("2026-05-08T18:00:00Z")));
    given(
            fundValueRepository.findValuesBetweenDatesForKeys(
                List.of("SGAS.XETRA"), LocalDate.of(2026, 5, 7), LocalDate.of(2026, 5, 8)))
        .willReturn(values);

    mockMvc
        .perform(
            get("/v1/index-values")
                .param("keys", "SGAS.XETRA")
                .param("format", "csv")
                .param("startDate", "2026-05-07")
                .param("endDate", "2026-05-08"))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
        .andExpect(
            content()
                .string(
                    "key,date,value,provider\r\n"
                        + "SGAS.XETRA,2026-05-07,12.990,EODHD\r\n"
                        + "SGAS.XETRA,2026-05-08,13.028,EODHD\r\n"));
  }

  @Test
  void getIndexValues_unknownKeys_returnsEmptyCsv() throws Exception {
    given(fundValueRepository.findLatestValuesByKeys(List.of("UNKNOWN.KEY"))).willReturn(List.of());

    mockMvc
        .perform(get("/v1/index-values").param("keys", "UNKNOWN.KEY").param("format", "csv"))
        .andExpect(status().isOk())
        .andExpect(content().string("key,date,value,provider\r\n"));
  }

  @Test
  void getIndexValues_missingKeysParam_returnsBadRequest() throws Exception {
    mockMvc
        .perform(get("/v1/index-values").param("format", "csv"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getIndexValues_emptyKeysParam_returnsBadRequest() throws Exception {
    mockMvc
        .perform(get("/v1/index-values").param("keys", "").param("format", "csv"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getIndexValues_tooManyKeys_returnsBadRequest() throws Exception {
    String tooManyKeys =
        IntStream.rangeClosed(1, MAX_KEYS + 1)
            .mapToObj(i -> "KEY" + i)
            .collect(Collectors.joining(","));

    mockMvc
        .perform(get("/v1/index-values").param("keys", tooManyKeys).param("format", "csv"))
        .andExpect(status().isBadRequest());
  }
}
