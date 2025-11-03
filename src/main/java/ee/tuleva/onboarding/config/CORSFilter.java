package ee.tuleva.onboarding.config;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.GenericFilterBean;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CORSFilter extends GenericFilterBean {

  @Value("${frontend.url}")
  private String frontendUrl;

  @Value("${ecs.frontend.url:#{null}}")
  private String ecsPensionFrontendUrl;

  private List<String> allowedOrigins;

  @PostConstruct
  public void init() {
    allowedOrigins = new java.util.ArrayList<>(Arrays.asList(frontendUrl, "https://tuleva.ee"));

    if (ecsPensionFrontendUrl != null && !ecsPensionFrontendUrl.isEmpty()) {
      allowedOrigins.add(ecsPensionFrontendUrl);
    }

    log.info("CORS: Allowed origins: {}", allowedOrigins);
  }

  @Override
  public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
      throws IOException, ServletException {
    HttpServletResponse response = (HttpServletResponse) res;
    HttpServletRequest request = (HttpServletRequest) req;
    String origin = request.getHeader("Origin");
    String allowedOriginResponse = allowedOrigins.contains(origin) ? origin : frontendUrl;
    response.setHeader("Access-Control-Allow-Origin", allowedOriginResponse);
    response.setHeader("Access-Control-Allow-Methods", "POST, PUT, GET, OPTIONS, DELETE");
    response.setHeader("Access-Control-Max-Age", "3600");
    response.setHeader("Access-Control-Allow-Headers", "Authorization");
    response.setHeader("Access-Control-Allow-Credentials", "true");
    response.setHeader(
        "P3P",
        "CP=\"ALL IND DSP COR ADM CONo CUR CUSo IVAo IVDo PSA PSD TAI TELo OUR SAMo CNT COM INT NAV ONL PHY PRE PUR UNI\"");

    List<String> allowedHeaders =
        Arrays.asList(
            "x-requested-with",
            "x-statistics-identifier",
            HttpHeaders.CONTENT_TYPE,
            HttpHeaders.AUTHORIZATION,
            HttpHeaders.USER_AGENT,
            HttpHeaders.ORIGIN,
            HttpHeaders.ACCEPT);
    response.setHeader("Access-Control-Allow-Headers", String.join(", ", allowedHeaders));

    if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
      response.setStatus(HttpServletResponse.SC_OK);
    } else {
      chain.doFilter(req, res);
    }
  }
}
