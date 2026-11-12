package ee.tuleva.onboarding.comparisons.admin;

import static ee.tuleva.onboarding.comparisons.admin.IndexValueExport.MAX_KEYS;
import static ee.tuleva.onboarding.comparisons.admin.IndexValueExport.MAX_ROWS;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.admin.AdminTokenValidator;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.IndexValueReader;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(IndexValueController.class)
@Import(IndexValueExport.class)
@WithMockUser
class IndexValueControllerTest {

  private static final String TOKEN = "test-admin-token";
  private static final String SGAS = "SGAS.XETRA";
  private static final String ISHARES = "IE00BFNM3G45.XETR";
  private static final LocalDate MAY_7 = LocalDate.of(2026, 5, 7);
  private static final LocalDate MAY_8 = LocalDate.of(2026, 5, 8);

  @Autowired private MockMvc mockMvc;

  @MockitoBean private FundValueRepository fundValueRepository;
  @MockitoBean private IndexValueReader indexValueReader;
  @MockitoBean private AdminTokenValidator tokenValidator;

  @Test
  void theLatestValueOfEachKey_isReturnedAsCsv_orderedByKey() throws Exception {
    given(indexValueReader.findLatestValuesByKeys(List.of(SGAS, ISHARES)))
        .willReturn(
            List.of(
                value(ISHARES, MAY_8, "13.028", "DEUTSCHE_BOERSE"),
                value(SGAS, MAY_8, "13.028", "EODHD")));

    mockMvc
        .perform(
            get("/admin/index-values")
                .header("X-Admin-Token", TOKEN)
                .param("keys", SGAS + "," + ISHARES))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(
            content()
                .string(
                    "key,date,value,provider,updatedAt\r\n"
                        + "IE00BFNM3G45.XETR,2026-05-08,13.028,DEUTSCHE_BOERSE,2026-05-08T18:00:00Z\r\n"
                        + "SGAS.XETRA,2026-05-08,13.028,EODHD,2026-05-08T18:00:00Z\r\n"));
  }

  @Test
  void theValuesBetweenTwoDates_areReturnedAsCsv_orderedByKeyAndDate() throws Exception {
    givenKnownKeys(SGAS);
    given(indexValueReader.countValuesBetweenDatesForKeys(List.of(SGAS), MAY_7, MAY_8))
        .willReturn(2L);
    given(indexValueReader.findValuesBetweenDatesForKeys(List.of(SGAS), MAY_7, MAY_8, MAX_ROWS + 1))
        .willReturn(
            List.of(value(SGAS, MAY_7, "12.990", "EODHD"), value(SGAS, MAY_8, "13.028", "EODHD")));

    mockMvc
        .perform(
            get("/admin/index-values")
                .header("X-Admin-Token", TOKEN)
                .param("keys", SGAS)
                .param("startDate", "2026-05-07")
                .param("endDate", "2026-05-08"))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
        .andExpect(
            content()
                .string(
                    "key,date,value,provider,updatedAt\r\n"
                        + "SGAS.XETRA,2026-05-07,12.990,EODHD,2026-05-07T18:00:00Z\r\n"
                        + "SGAS.XETRA,2026-05-08,13.028,EODHD,2026-05-08T18:00:00Z\r\n"));
  }

  @Test
  void aKeyWithNoValuesAtAll_isRejectedAsUnknown_ratherThanLeftOutOfTheCsv() throws Exception {
    given(indexValueReader.findLatestValuesByKeys(List.of(SGAS, "TYPO.KEY")))
        .willReturn(List.of(value(SGAS, MAY_8, "13.028", "EODHD")));

    mockMvc
        .perform(
            get("/admin/index-values")
                .header("X-Admin-Token", TOKEN)
                .param("keys", SGAS + ",TYPO.KEY"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void aKeyWithNoValuesAtAll_isRejectedAsUnknownForADateRangeToo_beforeAnyRowIsCounted()
      throws Exception {
    givenKnownKeys(SGAS);

    mockMvc
        .perform(
            get("/admin/index-values")
                .header("X-Admin-Token", TOKEN)
                .param("keys", SGAS + ",TYPO.KEY")
                .param("startDate", "2026-05-07")
                .param("endDate", "2026-05-08"))
        .andExpect(status().isBadRequest());
    then(fundValueRepository).should().findLatestDateByKeys(Set.of(SGAS, "TYPO.KEY"));
    then(fundValueRepository).shouldHaveNoMoreInteractions();
  }

  @Test
  void aKnownKeyWithNoValuesInTheRange_returnsJustTheHeader() throws Exception {
    givenKnownKeys(SGAS);
    given(indexValueReader.countValuesBetweenDatesForKeys(List.of(SGAS), MAY_7, MAY_8))
        .willReturn(0L);
    given(indexValueReader.findValuesBetweenDatesForKeys(List.of(SGAS), MAY_7, MAY_8, MAX_ROWS + 1))
        .willReturn(List.of());

    mockMvc
        .perform(
            get("/admin/index-values")
                .header("X-Admin-Token", TOKEN)
                .param("keys", SGAS)
                .param("startDate", "2026-05-07")
                .param("endDate", "2026-05-08"))
        .andExpect(status().isOk())
        .andExpect(content().string("key,date,value,provider,updatedAt\r\n"));
  }

  @Test
  void aTokenTheValidatorRejects_getsItsStatus_andNoIndexValueIsRead() throws Exception {
    willThrow(new ResponseStatusException(UNAUTHORIZED))
        .given(tokenValidator)
        .validateReadAccess("wrong-token");

    mockMvc
        .perform(
            get("/admin/index-values").header("X-Admin-Token", "wrong-token").param("keys", SGAS))
        .andExpect(status().isUnauthorized());
    then(fundValueRepository).shouldHaveNoInteractions();
    then(indexValueReader).shouldHaveNoInteractions();
  }

  @Test
  void aTokenTheValidatorRejects_getsItsStatusForADateRangeToo_andNoIndexValueIsRead()
      throws Exception {
    willThrow(new ResponseStatusException(UNAUTHORIZED))
        .given(tokenValidator)
        .validateReadAccess("wrong-token");

    mockMvc
        .perform(
            get("/admin/index-values")
                .header("X-Admin-Token", "wrong-token")
                .param("keys", SGAS)
                .param("startDate", "2026-05-07")
                .param("endDate", "2026-05-08"))
        .andExpect(status().isUnauthorized());
    then(fundValueRepository).shouldHaveNoInteractions();
    then(indexValueReader).shouldHaveNoInteractions();
  }

  @Test
  void aMissingKeysParam_isRejected() throws Exception {
    mockMvc
        .perform(get("/admin/index-values").header("X-Admin-Token", TOKEN))
        .andExpect(status().isBadRequest());
  }

  @Test
  void anEmptyKeysParam_isRejected() throws Exception {
    mockMvc
        .perform(get("/admin/index-values").header("X-Admin-Token", TOKEN).param("keys", ""))
        .andExpect(status().isBadRequest());
  }

  @Test
  void moreKeysThanTheLimit_areRejected() throws Exception {
    String tooManyKeys =
        IntStream.rangeClosed(1, MAX_KEYS + 1).mapToObj(i -> "KEY" + i).collect(joining(","));

    mockMvc
        .perform(
            get("/admin/index-values").header("X-Admin-Token", TOKEN).param("keys", tooManyKeys))
        .andExpect(status().isBadRequest());
  }

  @Test
  void oneHalfOfADateRange_isRejected_insteadOfSilentlyReturningLatestValues() throws Exception {
    mockMvc
        .perform(
            get("/admin/index-values")
                .header("X-Admin-Token", TOKEN)
                .param("keys", SGAS)
                .param("startDate", "2026-05-07"))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            get("/admin/index-values")
                .header("X-Admin-Token", TOKEN)
                .param("keys", SGAS)
                .param("endDate", "2026-05-08"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void aRangeThatEndsBeforeItStarts_isRejected() throws Exception {
    mockMvc
        .perform(
            get("/admin/index-values")
                .header("X-Admin-Token", TOKEN)
                .param("keys", SGAS)
                .param("startDate", "2026-05-08")
                .param("endDate", "2026-05-07"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void aDateNotInIsoFormat_isRejected_ratherThanReadInTheServersLocale() throws Exception {
    mockMvc
        .perform(
            get("/admin/index-values")
                .header("X-Admin-Token", TOKEN)
                .param("keys", SGAS)
                .param("startDate", "5/7/26")
                .param("endDate", "5/8/26"))
        .andExpect(status().isBadRequest());
    then(fundValueRepository).shouldHaveNoInteractions();
    then(indexValueReader).shouldHaveNoInteractions();
  }

  @Test
  void aResultTooLargeToReturn_isRefusedFromItsCount_withoutReadingTheRows() throws Exception {
    givenKnownKeys(SGAS);
    given(
            indexValueReader.countValuesBetweenDatesForKeys(
                List.of(SGAS), LocalDate.of(2020, 1, 1), MAY_8))
        .willReturn(MAX_ROWS + 1L);

    mockMvc
        .perform(
            get("/admin/index-values")
                .header("X-Admin-Token", TOKEN)
                .param("keys", SGAS)
                .param("startDate", "2020-01-01")
                .param("endDate", "2026-05-08"))
        .andExpect(status().isBadRequest());
    then(indexValueReader)
        .should(never())
        .findValuesBetweenDatesForKeys(any(), any(), any(), anyInt());
  }

  @Test
  void rowsAddedAfterTheCount_cannotPushTheCsvPastTheRowLimit() throws Exception {
    givenKnownKeys(SGAS);
    given(indexValueReader.countValuesBetweenDatesForKeys(List.of(SGAS), MAY_7, MAY_8))
        .willReturn((long) MAX_ROWS);
    given(indexValueReader.findValuesBetweenDatesForKeys(List.of(SGAS), MAY_7, MAY_8, MAX_ROWS + 1))
        .willReturn(
            IntStream.rangeClosed(0, MAX_ROWS)
                .mapToObj(i -> value(SGAS, MAY_8, "13.028", "EODHD"))
                .toList());

    mockMvc
        .perform(
            get("/admin/index-values")
                .header("X-Admin-Token", TOKEN)
                .param("keys", SGAS)
                .param("startDate", "2026-05-07")
                .param("endDate", "2026-05-08"))
        .andExpect(status().isBadRequest());
  }

  private void givenKnownKeys(String... keys) {
    given(fundValueRepository.findLatestDateByKeys(any()))
        .willAnswer(
            invocation -> {
              Set<String> requested = invocation.getArgument(0);
              return requested.stream()
                  .filter(Set.of(keys)::contains)
                  .collect(toMap(key -> key, key -> MAY_8));
            });
  }

  private static FundValue value(String key, LocalDate date, String value, String provider) {
    return new FundValue(
        key, date, new BigDecimal(value), provider, Instant.parse(date + "T18:00:00Z"));
  }
}
