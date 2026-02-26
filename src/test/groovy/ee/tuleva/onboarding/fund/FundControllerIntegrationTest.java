package ee.tuleva.onboarding.fund;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FundControllerIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private FundValueRepository repository;

  @BeforeEach
  void setup() {
    repository.saveAll(
        List.of(
            new FundValue("EE3600109435", LocalDate.of(2023, 1, 1), new BigDecimal("100.00")),
            new FundValue("EE3600109435", LocalDate.of(2023, 1, 2), new BigDecimal("100.50")),
            new FundValue("EE3600109435", LocalDate.of(2023, 1, 3), new BigDecimal("101.25"))));
  }

  @Test
  void exportFundNav_shouldReturnCsv_whenDataExists() throws Exception {
    mockMvc
        .perform(
            get("/v1/funds/EE3600109435/nav")
                .param("startDate", "2023-01-01")
                .param("endDate", "2023-01-02"))
        .andExpect(status().isOk())
        .andExpect(content().contentType("text/csv; charset=UTF-8"))
        .andExpect(header().string("Content-Disposition", containsString("attachment")))
        .andExpect(content().string(containsString("date,nav")))
        .andExpect(content().string(containsString("2023-01-01,100.00")))
        .andExpect(content().string(containsString("2023-01-02,100.50")));
  }

  @Test
  void exportFundNav_shouldReturn404_whenNoDataExists() throws Exception {
    mockMvc
        .perform(
            get("/v1/funds/EE9999999999/nav")
                .param("startDate", "2023-01-01")
                .param("endDate", "2023-01-02"))
        .andExpect(status().isNotFound());
  }

  @Test
  void exportFundNav_shouldReturn404_whenIsinDoesNotStartWithEE() throws Exception {
    mockMvc
        .perform(
            get("/v1/funds/LV1234567890/nav")
                .param("startDate", "2023-01-01")
                .param("endDate", "2023-01-02"))
        .andExpect(status().isNotFound());
  }

  @Test
  void exportFundNav_shouldReturn400_whenInvalidDateFormat() throws Exception {
    mockMvc
        .perform(
            get("/v1/funds/EE3600109435/nav")
                .param("startDate", "invalid-date")
                .param("endDate", "2023-01-02"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void exportFundNav_shouldUseDefaultDates_whenNoParametersProvided() throws Exception {
    mockMvc
        .perform(get("/v1/funds/EE3600109435/nav"))
        .andExpect(status().isOk())
        .andExpect(content().contentType("text/csv; charset=UTF-8"))
        .andExpect(content().string(containsString("date,nav")))
        .andExpect(content().string(containsString("2023-01-01,100.00")))
        .andExpect(content().string(containsString("2023-01-02,100.50")))
        .andExpect(content().string(containsString("2023-01-03,101.25")));
  }

  @Test
  void exportFundNav_shouldUseDefaultStartDate_whenOnlyEndDateProvided() throws Exception {
    mockMvc
        .perform(get("/v1/funds/EE3600109435/nav").param("endDate", "2023-01-02"))
        .andExpect(status().isOk())
        .andExpect(content().contentType("text/csv; charset=UTF-8"))
        .andExpect(content().string(containsString("2023-01-01,100.00")))
        .andExpect(content().string(containsString("2023-01-02,100.50")));
  }

  @Test
  void exportFundNav_shouldUseDefaultEndDate_whenOnlyStartDateProvided() throws Exception {
    mockMvc
        .perform(get("/v1/funds/EE3600109435/nav").param("startDate", "2023-01-01"))
        .andExpect(status().isOk())
        .andExpect(content().contentType("text/csv; charset=UTF-8"))
        .andExpect(content().string(containsString("2023-01-01,100.00")))
        .andExpect(content().string(containsString("2023-01-02,100.50")))
        .andExpect(content().string(containsString("2023-01-03,101.25")));
  }

  @Test
  void exportFundNav_shouldSetCorrectHeaders_whenExporting() throws Exception {
    mockMvc
        .perform(
            get("/v1/funds/EE3600109435/nav")
                .param("startDate", "2023-01-01")
                .param("endDate", "2023-01-02"))
        .andExpect(status().isOk())
        .andExpect(
            header()
                .string(
                    "Content-Disposition",
                    containsString("fund-nav-EE3600109435-2023-01-01-2023-01-02.csv")));
  }
}
