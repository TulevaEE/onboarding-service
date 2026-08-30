package ee.tuleva.onboarding.epis;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.auth.ServiceTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

@ExtendWith(MockitoExtension.class)
class EpisRequestHeadersTest {

  @Mock ServiceTokenProvider serviceTokenProvider;

  EpisRequestHeaders episRequestHeaders;

  @BeforeEach
  void setUp() {
    episRequestHeaders = new EpisRequestHeaders(serviceTokenProvider);
  }

  @Test
  void forToken_setsJsonContentTypeAndBearerAuthorizationHeader() {
    HttpHeaders headers = episRequestHeaders.forToken("some-token");

    assertThat(headers.getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    assertThat(headers.get("Authorization")).containsExactly("Bearer some-token");
  }

  @Test
  void entityFor_wrapsTokenInEntityWithHeaders() {
    var entity = episRequestHeaders.entityFor("some-token");

    assertThat(entity.getBody()).isNull();
    assertThat(entity.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    assertThat(entity.getHeaders().get("Authorization")).containsExactly("Bearer some-token");
  }
}
