package ee.tuleva.onboarding.fund;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(FundController.class)
@WithMockUser
class FundControllerNavHistoryTest {

  private static final String ISIN = "EE0000003283";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private FundService fundService;

  @Test
  void getNavHistory_returnsJsonWithCacheControlHeader() throws Exception {
    List<NavValueResponse> navValues =
        List.of(
            new NavValueResponse(LocalDate.of(2026, 2, 3), new BigDecimal("1.0000")),
            new NavValueResponse(LocalDate.of(2026, 2, 4), new BigDecimal("1.0012")));
    given(fundService.getNavHistory(ISIN, null, null)).willReturn(navValues);

    mockMvc
        .perform(get("/v1/funds/{isin}/nav", ISIN))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(jsonPath("$[0].date", is("2026-02-03")))
        .andExpect(jsonPath("$[0].value", is(1.0)))
        .andExpect(jsonPath("$[1].date", is("2026-02-04")))
        .andExpect(jsonPath("$[1].value", is(1.0012)));
  }

  @Test
  void getNavHistory_withCsvFormat_returnsCsvWithAttachmentAndCacheControl() throws Exception {
    byte[] csvBytes = "Kuupäev;NAV (EUR)\r\n03.02.2026;1.0000\r\n".getBytes("UTF-8");
    given(fundService.getNavHistoryCsv(ISIN, null, null)).willReturn(csvBytes);

    mockMvc
        .perform(get("/v1/funds/{isin}/nav", ISIN).param("format", "csv"))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
        .andExpect(
            header().string("Content-Disposition", "attachment; filename=\"nav-" + ISIN + ".csv\""))
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(content().bytes(csvBytes));
  }

  @Test
  void getNavHistory_unknownIsin_returnsNotFoundWithNoStore() throws Exception {
    willThrow(new ResponseStatusException(NOT_FOUND))
        .given(fundService)
        .getNavHistory("UNKNOWN", null, null);

    mockMvc
        .perform(get("/v1/funds/{isin}/nav", "UNKNOWN"))
        .andExpect(status().isNotFound())
        .andExpect(header().string("Cache-Control", "no-store"));
  }

  @Test
  void getNavHistory_unknownIsinCsv_returnsNotFoundWithNoStore() throws Exception {
    willThrow(new ResponseStatusException(NOT_FOUND))
        .given(fundService)
        .getNavHistoryCsv("UNKNOWN", null, null);

    mockMvc
        .perform(get("/v1/funds/{isin}/nav", "UNKNOWN").param("format", "csv"))
        .andExpect(status().isNotFound())
        .andExpect(header().string("Cache-Control", "no-store"));
  }
}
