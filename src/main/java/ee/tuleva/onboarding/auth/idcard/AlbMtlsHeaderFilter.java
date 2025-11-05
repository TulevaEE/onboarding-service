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

      // ALB provides the certificate in percent-encoded format (RFC 3986)
      // We must decode %XX sequences but preserve + signs (they're not spaces in base64)
      // URLDecoder.decode() incorrectly treats + as space, so we decode manually
      String decodedCertificate = percentDecode(clientCertificate).trim();

      // Comprehensive logging for debugging certificate decoding
      log.info("=== Certificate Decoding Debug ===");
      log.info("Raw ALB certificate length: {} chars", clientCertificate.length());
      log.info(
          "Raw ALB certificate (first 150 chars): {}",
          clientCertificate.substring(0, Math.min(150, clientCertificate.length())));
      log.info(
          "Raw ALB certificate (last 150 chars): {}",
          clientCertificate.substring(Math.max(0, clientCertificate.length() - 150)));

      // Check for + signs in raw certificate
      long plusCount = clientCertificate.chars().filter(ch -> ch == '+').count();
      long percentCount = clientCertificate.chars().filter(ch -> ch == '%').count();
      log.info("Raw certificate contains {} plus signs, {} percent signs", plusCount, percentCount);

      log.info("Decoded certificate length: {} chars", decodedCertificate.length());
      log.info(
          "Decoded certificate (first 150 chars): {}",
          decodedCertificate.substring(0, Math.min(150, decodedCertificate.length())));
      log.info(
          "Decoded certificate (last 150 chars): {}",
          decodedCertificate.substring(Math.max(0, decodedCertificate.length() - 150)));

      // Check for spaces and carriage returns in decoded certificate
      long spaceCount = decodedCertificate.chars().filter(ch -> ch == ' ').count();
      long plusCountDecoded = decodedCertificate.chars().filter(ch -> ch == '+').count();
      long crCount = decodedCertificate.chars().filter(ch -> ch == '\r').count();
      long lfCount = decodedCertificate.chars().filter(ch -> ch == '\n').count();
      log.info(
          "Decoded certificate contains {} spaces, {} plus signs, {} CR (\\r), {} LF (\\n)",
          spaceCount,
          plusCountDecoded,
          crCount,
          lfCount);

      // Show the exact bytes around problematic areas
      if (decodedCertificate.length() > 100) {
        String middle =
            decodedCertificate.substring(
                Math.max(0, decodedCertificate.length() / 2 - 50),
                Math.min(decodedCertificate.length(), decodedCertificate.length() / 2 + 50));
        log.info("Decoded certificate (middle 100 chars): {}", middle);
      }

      // Log the ENTIRE decoded certificate as escaped string (shows \r, \n, etc.)
      log.info("FULL DECODED CERTIFICATE (escaped):");
      log.info(decodedCertificate.replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t"));

      // Also log as hex bytes for first 200 chars to see exact encoding
      byte[] certBytes = decodedCertificate.getBytes(java.nio.charset.StandardCharsets.UTF_8);
      StringBuilder hexDump = new StringBuilder();
      for (int i = 0; i < Math.min(200, certBytes.length); i++) {
        hexDump.append(String.format("%02X ", certBytes[i]));
        if ((i + 1) % 16 == 0) hexDump.append("\n");
      }
      log.info("Certificate bytes (hex, first 200 chars):\n{}", hexDump);

      log.info("=== End Certificate Debug ===");

      this.translatedHeaders = new HashMap<>();
      translatedHeaders.put(NGINX_CLIENT_VERIFY_HEADER, NGINX_VERIFY_SUCCESS);
      translatedHeaders.put(NGINX_CLIENT_CERT_HEADER, decodedCertificate);
    }

    //     Decodes percent-encoded strings (RFC 3986) without treating + as space.
    //     ALB uses pure percent-encoding where only %XX sequences need decoding.
    private static String percentDecode(String encoded) {
      StringBuilder decoded = new StringBuilder(encoded.length());
      int i = 0;
      while (i < encoded.length()) {
        char c = encoded.charAt(i);
        if (c == '%' && i + 2 < encoded.length()) {
          try {
            // Decode %XX to the corresponding byte
            int value = Integer.parseInt(encoded.substring(i + 1, i + 3), 16);
            decoded.append((char) value);
            i += 3;
          } catch (NumberFormatException e) {
            // Invalid hex sequence, keep as-is
            decoded.append(c);
            i++;
          }
        } else {
          // Keep all other characters as-is (including +)
          decoded.append(c);
          i++;
        }
      }
      return decoded.toString();
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
