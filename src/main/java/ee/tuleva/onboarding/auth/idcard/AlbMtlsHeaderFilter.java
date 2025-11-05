package ee.tuleva.onboarding.auth.idcard;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.IOException;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

//  Translates AWS ALB mutual TLS (mTLS) headers to NGINX-compatible headers for Estonian ID card
//  authentication.

@Slf4j
@Component
@Order(1) // Run early in filter chain, before authentication filters
public class AlbMtlsHeaderFilter implements Filter {

  // ALB mTLS headers (injected when client certificate is validated)
  private static final String ALB_CLIENT_CERT_HEADER = "x-amzn-mtls-clientcert-leaf";
  private static final String ALB_CLIENT_CERT_SUBJECT = "x-amzn-mtls-clientcert-subject";
  private static final String ALB_CLIENT_CERT_ISSUER = "x-amzn-mtls-clientcert-issuer";
  private static final String ALB_CLIENT_CERT_SERIAL = "x-amzn-mtls-clientcert-serial-number";

  // NGINX headers (expected by existing ID card auth code)
  private static final String NGINX_CLIENT_VERIFY_HEADER = "ssl-client-verify";
  private static final String NGINX_CLIENT_CERT_HEADER = "ssl-client-cert";
  private static final String NGINX_VERIFY_SUCCESS = "SUCCESS";

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {

    HttpServletRequest httpRequest = (HttpServletRequest) request;

    String requestUri = httpRequest.getRequestURI();
    if (requestUri == null || !requestUri.startsWith("/idLogin")) {
      chain.doFilter(request, response);
      return;
    }

    // Check if ALB mTLS headers are present (indicates ECS deployment)
    String albClientCert = httpRequest.getHeader(ALB_CLIENT_CERT_HEADER);

    if (albClientCert != null && !albClientCert.isEmpty()) {
      log.debug("ALB mTLS headers detected for /idLogin - translating to NGINX format");

      String subject = httpRequest.getHeader(ALB_CLIENT_CERT_SUBJECT);
      String issuer = httpRequest.getHeader(ALB_CLIENT_CERT_ISSUER);
      String serialNumber = httpRequest.getHeader(ALB_CLIENT_CERT_SERIAL);

      log.info(
          "Estonian ID card authentication via ALB mTLS - Subject: {}, Issuer: {}, Serial: {}",
          subject,
          issuer,
          serialNumber);

      HttpServletRequestWrapper wrapper =
          new MtlsHeaderTranslatingRequest(httpRequest, albClientCert);

      chain.doFilter(wrapper, response);
    } else {
      log.debug("No ALB mTLS headers - passing through (NGINX or no client certificate)");
      chain.doFilter(request, response);
    }
  }

  private static class MtlsHeaderTranslatingRequest extends HttpServletRequestWrapper {

    private final Map<String, String> translatedHeaders;

    public MtlsHeaderTranslatingRequest(HttpServletRequest request, String clientCertificate) {
      super(request);

      this.translatedHeaders = new HashMap<>();
      translatedHeaders.put(NGINX_CLIENT_VERIFY_HEADER, NGINX_VERIFY_SUCCESS);
      translatedHeaders.put(NGINX_CLIENT_CERT_HEADER, clientCertificate);
    }

    @Override
    public String getHeader(String name) {
      if (translatedHeaders.containsKey(name)) {
        return translatedHeaders.get(name);
      }
      return super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
      Set<String> headerNames = new HashSet<>();

      Enumeration<String> originalNames = super.getHeaderNames();
      while (originalNames.hasMoreElements()) {
        headerNames.add(originalNames.nextElement());
      }

      headerNames.addAll(translatedHeaders.keySet());
      return Collections.enumeration(headerNames);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
      if (translatedHeaders.containsKey(name)) {
        return Collections.enumeration(Collections.singletonList(translatedHeaders.get(name)));
      }
      return super.getHeaders(name);
    }
  }

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    log.info(
        "ALB mTLS header translation filter initialized - ready to translate x-amzn-mtls-* headers to ssl-client-* headers");
  }

  @Override
  public void destroy() {
    log.debug("ALB mTLS header translation filter destroyed");
  }
}
