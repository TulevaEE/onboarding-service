package ee.tuleva.onboarding.epis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.HttpMethod.GET;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class EpisRejoinPermissionSourceTest {

  @Mock private RestTemplate episRestTemplate;
  @Mock private EpisRequestHeaders requestHeaders;

  @Test
  void asksEpisWhetherTheLoggedInPersonMayRejoin() {
    HttpEntity<String> userEntity = new HttpEntity<>("user");
    given(requestHeaders.userEntity()).willReturn(userEntity);
    given(
            episRestTemplate.exchange(
                "http://epis/rejoin-permission", GET, userEntity, RejoinPermission.class))
        .willReturn(ResponseEntity.ok(new RejoinPermission(true)));

    var source = new EpisRejoinPermissionSource(episRestTemplate, requestHeaders, "http://epis");

    assertThat(source.getRejoinPermission()).isEqualTo(new RejoinPermission(true));
  }
}
