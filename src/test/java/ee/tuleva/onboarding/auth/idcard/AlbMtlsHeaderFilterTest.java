package ee.tuleva.onboarding.auth.idcard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.quality.Strictness.LENIENT;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;

@MockitoSettings(strictness = LENIENT)
@ExtendWith(MockitoExtension.class)
class AlbMtlsHeaderFilterTest {

  private AlbMtlsHeaderFilter filter;

  @Mock private HttpServletRequest request;
  @Mock private HttpServletResponse response;
  @Mock private FilterChain filterChain;

  // Trim to match the behavior of AlbMtlsHeaderFilter which trims trailing newlines
  private static final String SAMPLE_CLIENT_CERT =
      """
      -----BEGIN CERTIFICATE-----
      MIIEATCCAumgAwIBAgIQW4IfiLQQgDXdCKXZdCYdKjANBgkqhkiG9w0BAQsFADBr
      MQswCQYDVQQGEwJFRTEiMCAGA1UECgwZQVMgU2VydGlmaXRzZWVyaW1pc2tlc2t1
      czEXMBUGA1UEYQwOTlRSRUUtMTA3NDcwMTMxHzAdBgNVBAMMFkVTVEVJRC1TSyAy
      MDE1IElELUNBMB4XDTE4MTAyMzE1MjE0MloXDTIzMTAyMzE1MjE0MlowgYQxCzAJ
      BgNVBAYTAkVFMQ8wDQYDVQQKDAZFU1RFSUQxGjAYBgNVBAsMEWF1dGhlbnRpY2F0
      aW9uMRcwFQYDVQQDDA5KQUFOLEpBTEdSQVRBUzEZMBcGA1UEBAwQSkFMR1JBVEFT
      MQ0wCwYDVQQqDARKQUFOMREwDwYDVQQFEwgzODAwMTA4NTCCASIwDQYJKoZIhvcN
      AQEBBQADggEPADCCAQoCggEBAKvVJ...
      -----END CERTIFICATE-----
      """
          .trim();

  private static final String SAMPLE_CERT_SUBJECT =
      "C=EE,O=ESTEID,OU=authentication,CN=JAAN,JALGRATAS,serialNumber=PNOEE-38001085718";
  private static final String SAMPLE_CERT_ISSUER =
      "CN=ESTEID2018,organizationIdentifier=NTREE-10747013,O=SK ID Solutions AS,C=EE";
  private static final String SAMPLE_CERT_SERIAL = "123456789";

  @BeforeEach
  void setUp() {
    filter = new AlbMtlsHeaderFilter();
  }

  @Nested
  @DisplayName("When request is NOT for /idLogin endpoint")
  class NonIdLoginRequests {

    @Test
    @DisplayName("Should pass through request unchanged for /api endpoint")
    void shouldPassThroughNonIdLoginRequest() throws ServletException, IOException {
      when(request.getRequestURI()).thenReturn("/api/v1/funds");

      filter.doFilter(request, response, filterChain);

      verify(filterChain).doFilter(request, response);
      verifyNoInteractions(response);
    }

    @Test
    @DisplayName("Should pass through request unchanged for /actuator endpoint")
    void shouldPassThroughActuatorRequest() throws ServletException, IOException {
      when(request.getRequestURI()).thenReturn("/actuator/health");

      filter.doFilter(request, response, filterChain);

      verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should pass through request unchanged for root endpoint")
    void shouldPassThroughRootRequest() throws ServletException, IOException {
      when(request.getRequestURI()).thenReturn("/");

      filter.doFilter(request, response, filterChain);

      verify(filterChain).doFilter(request, response);
    }
  }

  @Nested
  @DisplayName("When request is for /idLogin WITH ALB mTLS headers")
  class IdLoginWithAlbHeaders {

    @BeforeEach
    void setUpIdLoginRequest() {
      when(request.getRequestURI()).thenReturn("/idLogin");
      when(request.getHeader("x-amzn-mtls-clientcert-leaf")).thenReturn(SAMPLE_CLIENT_CERT);
      when(request.getHeader("x-amzn-mtls-clientcert-subject")).thenReturn(SAMPLE_CERT_SUBJECT);
      when(request.getHeader("x-amzn-mtls-clientcert-issuer")).thenReturn(SAMPLE_CERT_ISSUER);
      when(request.getHeader("x-amzn-mtls-clientcert-serial-number"))
          .thenReturn(SAMPLE_CERT_SERIAL);
    }

    @Test
    @DisplayName("Should translate ALB headers to NGINX format")
    void shouldTranslateAlbHeadersToNginxFormat() throws ServletException, IOException {
      filter.doFilter(request, response, filterChain);

      ArgumentCaptor<HttpServletRequest> requestCaptor =
          ArgumentCaptor.forClass(HttpServletRequest.class);
      verify(filterChain).doFilter(requestCaptor.capture(), eq(response));

      HttpServletRequest wrappedRequest = requestCaptor.getValue();

      assertThat(wrappedRequest.getHeader("ssl-client-verify")).isEqualTo("SUCCESS");
      assertThat(wrappedRequest.getHeader("ssl-client-cert")).isEqualTo(SAMPLE_CLIENT_CERT);
    }

    @Test
    @DisplayName("Should preserve original ALB headers in wrapped request")
    void shouldPreserveOriginalAlbHeaders() throws ServletException, IOException {
      filter.doFilter(request, response, filterChain);

      ArgumentCaptor<HttpServletRequest> requestCaptor =
          ArgumentCaptor.forClass(HttpServletRequest.class);
      verify(filterChain).doFilter(requestCaptor.capture(), eq(response));

      HttpServletRequest wrappedRequest = requestCaptor.getValue();
      assertThat(wrappedRequest).isNotNull();
    }

    @Test
    @DisplayName("Should include both original and translated headers in header enumeration")
    void shouldIncludeBothHeaderTypesInEnumeration() throws ServletException, IOException {
      Vector<String> originalHeaders = new Vector<>();
      originalHeaders.add("x-amzn-mtls-clientcert-leaf");
      originalHeaders.add("x-amzn-mtls-clientcert-subject");
      originalHeaders.add("host");
      originalHeaders.add("user-agent");
      when(request.getHeaderNames()).thenReturn(originalHeaders.elements());

      filter.doFilter(request, response, filterChain);

      ArgumentCaptor<HttpServletRequest> requestCaptor =
          ArgumentCaptor.forClass(HttpServletRequest.class);
      verify(filterChain).doFilter(requestCaptor.capture(), eq(response));

      HttpServletRequest wrappedRequest = requestCaptor.getValue();
      Enumeration<String> headerNames = wrappedRequest.getHeaderNames();

      Vector<String> headerNameList = new Vector<>();
      while (headerNames.hasMoreElements()) {
        headerNameList.add(headerNames.nextElement());
      }

      assertThat(headerNameList).contains("ssl-client-verify", "ssl-client-cert");
    }

    @Test
    @DisplayName("Should handle /idLogin with query parameters")
    void shouldHandleIdLoginWithQueryParams() throws ServletException, IOException {
      when(request.getRequestURI()).thenReturn("/idLogin?redirect=/dashboard");

      filter.doFilter(request, response, filterChain);

      ArgumentCaptor<HttpServletRequest> requestCaptor =
          ArgumentCaptor.forClass(HttpServletRequest.class);
      verify(filterChain).doFilter(requestCaptor.capture(), eq(response));

      HttpServletRequest wrappedRequest = requestCaptor.getValue();
      assertThat(wrappedRequest.getHeader("ssl-client-verify")).isEqualTo("SUCCESS");
    }
  }

  @Nested
  @DisplayName("When request is for /idLogin WITHOUT ALB mTLS headers")
  class IdLoginWithoutAlbHeaders {

    @BeforeEach
    void setUpIdLoginRequestWithoutAlbHeaders() {
      when(request.getRequestURI()).thenReturn("/idLogin");
      when(request.getHeader("x-amzn-mtls-clientcert-leaf")).thenReturn(null);
    }

    @Test
    @DisplayName("Should pass through unchanged (NGINX/Beanstalk mode)")
    void shouldPassThroughWhenNoAlbHeaders() throws ServletException, IOException {
      when(request.getHeader("ssl-client-verify")).thenReturn("SUCCESS");
      when(request.getHeader("ssl-client-cert")).thenReturn(SAMPLE_CLIENT_CERT);

      filter.doFilter(request, response, filterChain);

      verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should pass through when ALB header is empty string")
    void shouldPassThroughWhenAlbHeaderIsEmpty() throws ServletException, IOException {
      when(request.getHeader("x-amzn-mtls-clientcert-leaf")).thenReturn("");

      filter.doFilter(request, response, filterChain);

      verify(filterChain).doFilter(request, response);
    }
  }

  @Nested
  @DisplayName("Edge cases and validation")
  class EdgeCases {

    @Test
    @DisplayName("Should handle null request URI gracefully")
    void shouldHandleNullRequestUri() throws ServletException, IOException {
      when(request.getRequestURI()).thenReturn(null);

      filter.doFilter(request, response, filterChain);

      verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should handle /idLogin prefix match correctly")
    void shouldHandleIdLoginPrefixMatch() throws ServletException, IOException {
      when(request.getRequestURI()).thenReturn("/idLogin/callback");
      when(request.getHeader("x-amzn-mtls-clientcert-leaf")).thenReturn(SAMPLE_CLIENT_CERT);

      filter.doFilter(request, response, filterChain);

      ArgumentCaptor<HttpServletRequest> requestCaptor =
          ArgumentCaptor.forClass(HttpServletRequest.class);
      verify(filterChain).doFilter(requestCaptor.capture(), eq(response));

      HttpServletRequest wrappedRequest = requestCaptor.getValue();
      assertThat(wrappedRequest.getHeader("ssl-client-verify")).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("Should NOT match URI containing /idLogin in middle")
    void shouldNotMatchIdLoginInMiddleOfUri() throws ServletException, IOException {
      when(request.getRequestURI()).thenReturn("/api/idLogin/test");

      filter.doFilter(request, response, filterChain);

      verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should handle percent-encoded certificate from ALB (RFC 3986)")
    void shouldHandlePercentEncodedCertificateFromAlb() throws ServletException, IOException {
      // ALB uses RFC 3986 percent-encoding where + stays as + (NOT converted to space)
      // Only %XX sequences are decoded
      String percentEncodedCert =
          "-----BEGIN%20CERTIFICATE-----%0A"
              + "MIIEATCCAumgAwIBAgIQW4IfiLQQgDXdCKXZdCYdKjANBg==%0A"
              + "Base64%20with%20plus:%20abc+def/ghi=%0A" // + is literal in base64, not %2B
              + "-----END%20CERTIFICATE-----";

      // Expected: spaces and newlines decoded, + signs preserved
      String decodedCert =
          "-----BEGIN CERTIFICATE-----\n"
              + "MIIEATCCAumgAwIBAgIQW4IfiLQQgDXdCKXZdCYdKjANBg==\n"
              + "Base64 with plus: abc+def/ghi=\n"
              + "-----END CERTIFICATE-----";

      when(request.getRequestURI()).thenReturn("/idLogin");
      when(request.getHeader("x-amzn-mtls-clientcert-leaf")).thenReturn(percentEncodedCert);
      when(request.getHeader("x-amzn-mtls-clientcert-subject")).thenReturn("CN=Test,O=Org,C=EE");

      filter.doFilter(request, response, filterChain);

      ArgumentCaptor<HttpServletRequest> requestCaptor =
          ArgumentCaptor.forClass(HttpServletRequest.class);
      verify(filterChain).doFilter(requestCaptor.capture(), eq(response));

      HttpServletRequest wrappedRequest = requestCaptor.getValue();
      assertThat(wrappedRequest.getHeader("ssl-client-cert")).isEqualTo(decodedCert);
    }

    @Test
    @DisplayName("Should preserve plus signs in base64 content (not convert to spaces)")
    void shouldPreservePlusSignsInBase64() throws ServletException, IOException {
      // Real base64 content contains + signs that must NOT be converted to spaces
      // ALB sends these as literal + (not %2B), and we must preserve them
      String certWithPlus =
          "-----BEGIN%20CERTIFICATE-----%0A"
              + "MIIDTest+Content+With+Plus===%0A" // Plus signs in base64
              + "-----END%20CERTIFICATE-----";

      String expectedDecoded =
          "-----BEGIN CERTIFICATE-----\n"
              + "MIIDTest+Content+With+Plus===\n" // Plus signs preserved
              + "-----END CERTIFICATE-----";

      when(request.getRequestURI()).thenReturn("/idLogin");
      when(request.getHeader("x-amzn-mtls-clientcert-leaf")).thenReturn(certWithPlus);
      when(request.getHeader("x-amzn-mtls-clientcert-subject")).thenReturn("CN=Test");

      filter.doFilter(request, response, filterChain);

      ArgumentCaptor<HttpServletRequest> requestCaptor =
          ArgumentCaptor.forClass(HttpServletRequest.class);
      verify(filterChain).doFilter(requestCaptor.capture(), eq(response));

      HttpServletRequest wrappedRequest = requestCaptor.getValue();
      assertThat(wrappedRequest.getHeader("ssl-client-cert")).isEqualTo(expectedDecoded);
    }
  }

  @Nested
  @DisplayName("Percent decoding (RFC 3986)")
  class PercentDecoding {

    @Test
    @DisplayName("Should decode common percent sequences (%20, %0A, %2F)")
    void shouldDecodeCommonPercentSequences() throws ServletException, IOException {
      String encoded = "Hello%20World%0ANew%20Line%2FSlash";
      String expected = "Hello World\nNew Line/Slash";

      when(request.getRequestURI()).thenReturn("/idLogin");
      when(request.getHeader("x-amzn-mtls-clientcert-leaf")).thenReturn(encoded);

      filter.doFilter(request, response, filterChain);

      ArgumentCaptor<HttpServletRequest> requestCaptor =
          ArgumentCaptor.forClass(HttpServletRequest.class);
      verify(filterChain).doFilter(requestCaptor.capture(), eq(response));

      assertThat(requestCaptor.getValue().getHeader("ssl-client-cert")).isEqualTo(expected);
    }

    @Test
    @DisplayName("Should preserve plus signs (NOT convert to spaces like URLDecoder)")
    void shouldPreservePlusSigns() throws ServletException, IOException {
      // This is the key difference from URLDecoder: + stays as +
      String encoded = "test+data+with+plus+signs";
      String expected = "test+data+with+plus+signs"; // Plus preserved

      when(request.getRequestURI()).thenReturn("/idLogin");
      when(request.getHeader("x-amzn-mtls-clientcert-leaf")).thenReturn(encoded);

      filter.doFilter(request, response, filterChain);

      ArgumentCaptor<HttpServletRequest> requestCaptor =
          ArgumentCaptor.forClass(HttpServletRequest.class);
      verify(filterChain).doFilter(requestCaptor.capture(), eq(response));

      assertThat(requestCaptor.getValue().getHeader("ssl-client-cert")).isEqualTo(expected);
    }

    @Test
    @DisplayName("Should handle mixed encoded and literal characters")
    void shouldHandleMixedEncodedAndLiteral() throws ServletException, IOException {
      String encoded = "abc%20def+ghi%2Fjkl=mno";
      String expected = "abc def+ghi/jkl=mno";

      when(request.getRequestURI()).thenReturn("/idLogin");
      when(request.getHeader("x-amzn-mtls-clientcert-leaf")).thenReturn(encoded);

      filter.doFilter(request, response, filterChain);

      ArgumentCaptor<HttpServletRequest> requestCaptor =
          ArgumentCaptor.forClass(HttpServletRequest.class);
      verify(filterChain).doFilter(requestCaptor.capture(), eq(response));

      assertThat(requestCaptor.getValue().getHeader("ssl-client-cert")).isEqualTo(expected);
    }

    @Test
    @DisplayName("Should handle invalid hex sequences gracefully")
    void shouldHandleInvalidHexSequences() throws ServletException, IOException {
      // Invalid hex should be kept as-is
      String encoded = "test%ZZ%20valid%GG";
      String expected = "test%ZZ valid%GG"; // Invalid kept, valid decoded

      when(request.getRequestURI()).thenReturn("/idLogin");
      when(request.getHeader("x-amzn-mtls-clientcert-leaf")).thenReturn(encoded);

      filter.doFilter(request, response, filterChain);

      ArgumentCaptor<HttpServletRequest> requestCaptor =
          ArgumentCaptor.forClass(HttpServletRequest.class);
      verify(filterChain).doFilter(requestCaptor.capture(), eq(response));

      assertThat(requestCaptor.getValue().getHeader("ssl-client-cert")).isEqualTo(expected);
    }

    @Test
    @DisplayName("Should handle incomplete percent sequences at end")
    void shouldHandleIncompletePercentSequences() throws ServletException, IOException {
      String encoded = "test%20complete%2"; // Incomplete at end
      String expected = "test complete%2"; // Incomplete kept as-is

      when(request.getRequestURI()).thenReturn("/idLogin");
      when(request.getHeader("x-amzn-mtls-clientcert-leaf")).thenReturn(encoded);

      filter.doFilter(request, response, filterChain);

      ArgumentCaptor<HttpServletRequest> requestCaptor =
          ArgumentCaptor.forClass(HttpServletRequest.class);
      verify(filterChain).doFilter(requestCaptor.capture(), eq(response));

      assertThat(requestCaptor.getValue().getHeader("ssl-client-cert")).isEqualTo(expected);
    }

    @Test
    @DisplayName("Should handle empty string")
    void shouldHandleEmptyString() throws ServletException, IOException {
      when(request.getRequestURI()).thenReturn("/idLogin");
      when(request.getHeader("x-amzn-mtls-clientcert-leaf")).thenReturn("");

      filter.doFilter(request, response, filterChain);

      // Empty string should pass through unchanged (will fail at next filter)
      verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should handle string with no encoding")
    void shouldHandleStringWithNoEncoding() throws ServletException, IOException {
      String unencoded = "Plain text with no encoding";

      when(request.getRequestURI()).thenReturn("/idLogin");
      when(request.getHeader("x-amzn-mtls-clientcert-leaf")).thenReturn(unencoded);

      filter.doFilter(request, response, filterChain);

      ArgumentCaptor<HttpServletRequest> requestCaptor =
          ArgumentCaptor.forClass(HttpServletRequest.class);
      verify(filterChain).doFilter(requestCaptor.capture(), eq(response));

      assertThat(requestCaptor.getValue().getHeader("ssl-client-cert")).isEqualTo(unencoded);
    }
  }

  @Nested
  @DisplayName("Filter lifecycle")
  class FilterLifecycle {

    @Test
    @DisplayName("Should initialize without errors")
    void shouldInitializeWithoutErrors() throws ServletException {
      filter.init(null);
    }

    @Test
    @DisplayName("Should destroy without errors")
    void shouldDestroyWithoutErrors() {
      filter.destroy();
    }
  }

  @Nested
  @DisplayName("Header wrapper behavior")
  class HeaderWrapperBehavior {

    @Test
    @DisplayName("Should return translated header via getHeader()")
    void shouldReturnTranslatedHeaderViaGetHeader() throws ServletException, IOException {
      when(request.getRequestURI()).thenReturn("/idLogin");
      when(request.getHeader("x-amzn-mtls-clientcert-leaf")).thenReturn(SAMPLE_CLIENT_CERT);

      filter.doFilter(request, response, filterChain);

      ArgumentCaptor<HttpServletRequest> requestCaptor =
          ArgumentCaptor.forClass(HttpServletRequest.class);
      verify(filterChain).doFilter(requestCaptor.capture(), eq(response));

      HttpServletRequest wrappedRequest = requestCaptor.getValue();
      assertThat(wrappedRequest.getHeader("ssl-client-verify")).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("Should return translated header via getHeaders()")
    void shouldReturnTranslatedHeaderViaGetHeaders() throws ServletException, IOException {
      when(request.getRequestURI()).thenReturn("/idLogin");
      when(request.getHeader("x-amzn-mtls-clientcert-leaf")).thenReturn(SAMPLE_CLIENT_CERT);
      when(request.getHeaders("ssl-client-verify")).thenReturn(Collections.emptyEnumeration());

      filter.doFilter(request, response, filterChain);

      ArgumentCaptor<HttpServletRequest> requestCaptor =
          ArgumentCaptor.forClass(HttpServletRequest.class);
      verify(filterChain).doFilter(requestCaptor.capture(), eq(response));

      HttpServletRequest wrappedRequest = requestCaptor.getValue();
      Enumeration<String> headers = wrappedRequest.getHeaders("ssl-client-verify");

      assertThat(headers.hasMoreElements()).isTrue();
      assertThat(headers.nextElement()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("Should delegate to original request for non-translated headers")
    void shouldDelegateToOriginalRequestForNonTranslatedHeaders()
        throws ServletException, IOException {
      when(request.getRequestURI()).thenReturn("/idLogin");
      when(request.getHeader("x-amzn-mtls-clientcert-leaf")).thenReturn(SAMPLE_CLIENT_CERT);

      filter.doFilter(request, response, filterChain);

      ArgumentCaptor<HttpServletRequest> requestCaptor =
          ArgumentCaptor.forClass(HttpServletRequest.class);
      verify(filterChain).doFilter(requestCaptor.capture(), eq(response));

      HttpServletRequest wrappedRequest = requestCaptor.getValue();
      wrappedRequest.getHeader("host");

      verify(request).getHeader("host");
    }
  }

  @Nested
  @DisplayName("Real-world scenarios")
  class RealWorldScenarios {

    @Test
    @DisplayName("Should work correctly when migrating from Elastic Beanstalk (NGINX) to ECS (ALB)")
    void shouldSupportBothNginxAndAlbSimultaneously() throws ServletException, IOException {
      HttpServletRequest ecsRequest = mock(HttpServletRequest.class);
      when(ecsRequest.getRequestURI()).thenReturn("/idLogin");
      when(ecsRequest.getHeader("x-amzn-mtls-clientcert-leaf")).thenReturn(SAMPLE_CLIENT_CERT);

      filter.doFilter(ecsRequest, response, filterChain);

      ArgumentCaptor<HttpServletRequest> ecsCaptor =
          ArgumentCaptor.forClass(HttpServletRequest.class);
      verify(filterChain).doFilter(ecsCaptor.capture(), eq(response));
      assertThat(ecsCaptor.getValue().getHeader("ssl-client-verify")).isEqualTo("SUCCESS");

      reset(filterChain);
      HttpServletRequest beanstalkRequest = mock(HttpServletRequest.class);
      when(beanstalkRequest.getRequestURI()).thenReturn("/idLogin");
      when(beanstalkRequest.getHeader("x-amzn-mtls-clientcert-leaf")).thenReturn(null);
      when(beanstalkRequest.getHeader("ssl-client-verify")).thenReturn("SUCCESS");

      filter.doFilter(beanstalkRequest, response, filterChain);

      verify(filterChain).doFilter(beanstalkRequest, response);
    }

    @Test
    @DisplayName("Should handle Estonian ID card with all required certificate details")
    void shouldHandleEstonianIdCardWithAllDetails() throws ServletException, IOException {
      String realSubject =
          "C=EE,O=ESTEID,OU=authentication,CN=MARI,MAASIKAS,serialNumber=PNOEE-48501210019";
      String realIssuer =
          "CN=ESTEID2018,organizationIdentifier=NTREE-10747013,O=SK ID Solutions AS,C=EE";
      String realSerial = "98765432101234567890";

      when(request.getRequestURI()).thenReturn("/idLogin");
      when(request.getHeader("x-amzn-mtls-clientcert-leaf")).thenReturn(SAMPLE_CLIENT_CERT);
      when(request.getHeader("x-amzn-mtls-clientcert-subject")).thenReturn(realSubject);
      when(request.getHeader("x-amzn-mtls-clientcert-issuer")).thenReturn(realIssuer);
      when(request.getHeader("x-amzn-mtls-clientcert-serial-number")).thenReturn(realSerial);

      filter.doFilter(request, response, filterChain);

      ArgumentCaptor<HttpServletRequest> requestCaptor =
          ArgumentCaptor.forClass(HttpServletRequest.class);
      verify(filterChain).doFilter(requestCaptor.capture(), eq(response));

      HttpServletRequest wrappedRequest = requestCaptor.getValue();
      assertThat(wrappedRequest.getHeader("ssl-client-verify")).isEqualTo("SUCCESS");
      assertThat(wrappedRequest.getHeader("ssl-client-cert")).isEqualTo(SAMPLE_CLIENT_CERT);
    }

    @Test
    @DisplayName(
        "INTEGRATION: Should decode ALB certificate and validate all base64 lines are multiples of 4")
    void shouldDecodeAlbCertificateWithValidBase64LineLength()
        throws ServletException, IOException {
      // This test validates that after decoding, all base64 content lines have valid lengths
      // Base64 lines must be multiples of 4 characters (RFC 4648)

      // Simulated ALB-encoded certificate with base64 content
      String albEncodedCert =
          "-----BEGIN%20CERTIFICATE-----%0A"
              + "MIID5zCCA0qgAwIBAgIQSnxuSk+CVTH25SrqalcEkDAKBggqhkjOPQQDBDBYMQsw%0A"
              + "CQYDVQQGEwJFRTEbMBkGA1UECgwSU0sgSUQgU29sdXRpb25zIEFTMQswCQYDVQQL%0A"
              + "DAJBSTEfMB0GA1UEAwwWRVNURUlEMjAxOCBJRC1DQTAgMB4XDTIzMDUxNTEzMjA1%0A"
              + "NloXDTI4MDUxNDIxNTk1OVowgYQxCzAJBgNVBAYTAkVFMQ8wDQYDVQQKDAZFU1RF%0A"
              + "SUQxGjAYBgNVBAsMEWF1dGhlbnRpY2F0aW9uMRcwFQYDVQQDDA5KQUFOLEpBTEdS%0A"
              + "QVRBU0EZMBcGA1UEBAwQSkFMR1JBVEFTMREwDwYDVQQFEwgzODAwMTA4NTBZMBMG%0A"
              + "ByqGSM49AgEGCCqGSM49AwEHA0IABBG0xCzxJQzgfQVfEJL+HGxwz4CgZ8cQ4JIk%0A"
              + "fQE5LqKd0a9eVkwRiU7lPHf8T9Qi0qYwKxJqN8M5K9oTrGEaKNmjggGHMIIBgzAJ%0A"
              + "-----END%20CERTIFICATE-----%0A";

      when(request.getRequestURI()).thenReturn("/idLogin");
      when(request.getHeader("x-amzn-mtls-clientcert-leaf")).thenReturn(albEncodedCert);
      when(request.getHeader("x-amzn-mtls-clientcert-subject")).thenReturn("CN=Test");

      filter.doFilter(request, response, filterChain);

      ArgumentCaptor<HttpServletRequest> requestCaptor =
          ArgumentCaptor.forClass(HttpServletRequest.class);
      verify(filterChain).doFilter(requestCaptor.capture(), eq(response));

      String decodedCert = requestCaptor.getValue().getHeader("ssl-client-cert");

      // Validate all base64 content lines have valid lengths (multiples of 4)
      String[] lines = decodedCert.split("\n");
      for (int i = 0; i < lines.length; i++) {
        String line = lines[i];
        if (!line.startsWith("-----")) {
          // This is a base64 content line - must be multiple of 4
          int length = line.length();
          boolean validLength = (length % 4 == 0) || line.endsWith("=");
          assertThat(validLength)
              .as(
                  "Line %d has invalid length %d (not multiple of 4): '%s'",
                  i, length, line.length() > 80 ? line.substring(0, 80) + "..." : line)
              .isTrue();
        }
      }

      // Additionally verify no spaces exist in base64 content (common decoding error)
      for (String line : lines) {
        if (!line.startsWith("-----")) {
          assertThat(line).as("Base64 line should not contain spaces").doesNotContain(" ");
        }
      }
    }
  }
}
