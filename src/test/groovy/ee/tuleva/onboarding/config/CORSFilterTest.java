package ee.tuleva.onboarding.config;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.mock;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CORSFilterTest {

  private static final String FRONTEND_URL = "https://onboarding.tuleva.ee";

  private CORSFilter newFilter(String ecsPensionFrontendUrl) {
    CORSFilter filter = new CORSFilter();
    ReflectionTestUtils.setField(filter, "frontendUrl", FRONTEND_URL);
    ReflectionTestUtils.setField(filter, "ecsPensionFrontendUrl", ecsPensionFrontendUrl);
    filter.init();
    return filter;
  }

  @Test
  void initAllowsTheConfiguredFrontendAndTulevaOrigins() throws Exception {
    CORSFilter filter = newFilter(null);
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);
    given(request.getHeader("Origin")).willReturn("https://tuleva.ee");
    given(request.getMethod()).willReturn("GET");

    filter.doFilter(request, response, chain);

    verify(response).setHeader("Access-Control-Allow-Origin", "https://tuleva.ee");
  }

  @Test
  void initSkipsBlankEcsFrontendUrl() throws Exception {
    CORSFilter filter = newFilter("");
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);
    given(request.getHeader("Origin")).willReturn("https://ecs.tuleva.ee");
    given(request.getMethod()).willReturn("GET");

    filter.doFilter(request, response, chain);

    verify(response).setHeader("Access-Control-Allow-Origin", FRONTEND_URL);
  }

  @Test
  void initAddsTheConfiguredEcsFrontendUrlWhenPresent() throws Exception {
    CORSFilter filter = newFilter("https://ecs.tuleva.ee");
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);
    given(request.getHeader("Origin")).willReturn("https://ecs.tuleva.ee");
    given(request.getMethod()).willReturn("GET");

    filter.doFilter(request, response, chain);

    verify(response).setHeader("Access-Control-Allow-Origin", "https://ecs.tuleva.ee");
  }

  @Test
  void doFilterFallsBackToFrontendUrlForUnrecognizedOrigins() throws Exception {
    CORSFilter filter = newFilter(null);
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);
    given(request.getHeader("Origin")).willReturn("https://evil.example.com");
    given(request.getMethod()).willReturn("GET");

    filter.doFilter(request, response, chain);

    verify(response).setHeader("Access-Control-Allow-Origin", FRONTEND_URL);
  }

  @Test
  void doFilterSetsAllExpectedHeadersAndContinuesTheChainForNonOptionsRequests() throws Exception {
    CORSFilter filter = newFilter(null);
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);
    given(request.getHeader("Origin")).willReturn("https://tuleva.ee");
    given(request.getMethod()).willReturn("GET");

    filter.doFilter(request, response, chain);

    verify(response).setHeader("Access-Control-Allow-Methods", "POST, PUT, GET, OPTIONS, DELETE");
    verify(response).setHeader("Access-Control-Max-Age", "3600");
    verify(response).setHeader("Access-Control-Allow-Headers", "Authorization");
    verify(response).setHeader("Access-Control-Allow-Credentials", "true");
    verify(response)
        .setHeader(
            "P3P",
            "CP=\"ALL IND DSP COR ADM CONo CUR CUSo IVAo IVDo PSA PSD TAI TELo OUR SAMo CNT COM INT NAV ONL PHY PRE PUR UNI\"");
    verify(response)
        .setHeader(
            "Access-Control-Allow-Headers",
            "x-requested-with, x-statistics-identifier, Content-Type, Authorization, User-Agent, Origin, Accept");
    verify(response, never()).setStatus(HttpServletResponse.SC_OK);
    verify(chain).doFilter(request, response);
  }

  @Test
  void doFilterRespondsOkAndSkipsTheChainForOptionsRequests() throws Exception {
    CORSFilter filter = newFilter(null);
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);
    given(request.getHeader("Origin")).willReturn("https://tuleva.ee");
    given(request.getMethod()).willReturn("OPTIONS");

    filter.doFilter(request, response, chain);

    verify(response).setStatus(HttpServletResponse.SC_OK);
    verify(chain, never()).doFilter(request, response);
  }

  @Test
  void doFilterTreatsOptionsCaseInsensitively() throws Exception {
    CORSFilter filter = newFilter(null);
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);
    given(request.getHeader("Origin")).willReturn("https://tuleva.ee");
    given(request.getMethod()).willReturn("options");

    filter.doFilter(request, response, chain);

    verify(response).setStatus(HttpServletResponse.SC_OK);
    verify(chain, never()).doFilter(request, response);
  }
}
