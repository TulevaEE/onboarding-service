package ee.tuleva.onboarding.auth.capacity;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember;
import static ee.tuleva.onboarding.auth.role.RoleType.PERSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.role.ChildRepresentations;
import ee.tuleva.onboarding.auth.role.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class RestrictedCapacityFilterTest {

  private static final String WARD = "38001085718";
  private static final String GUARDIAN = "38812121215";

  @Mock private ChildRepresentations childRepresentations;

  private final MockHttpServletResponse response = new MockHttpServletResponse();
  private final MockFilterChain chain = new MockFilterChain();

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void blocksATransactionByAPersonUnderGuardianship() throws Exception {
    authenticate(ward());
    restrictedCapacity(WARD, true);

    filter().doFilter(post("/v1/mandates"), response, chain);

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getContentAsString()).contains("RESTRICTED_LEGAL_CAPACITY");
    assertThat(chain.getRequest()).isNull();
  }

  @Test
  void blocksEveryWriteMethod() throws Exception {
    authenticate(ward());
    restrictedCapacity(WARD, true);

    MockHttpServletRequest request =
        new MockHttpServletRequest("PUT", "/v1/mandates/1/signature/smartId");
    filter().doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(chain.getRequest()).isNull();
  }

  @Test
  void letsAPersonUnderGuardianshipReadTheirOwnAccount() throws Exception {
    authenticate(ward());

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/me");
    filter().doFilter(request, response, chain);

    assertThat(chain.getRequest()).isSameAs(request);
    verifyNoInteractions(childRepresentations);
  }

  @Test
  void letsTheGuardianTransactInTheWardsRole() throws Exception {
    AuthenticatedPerson guardian =
        sampleAuthenticatedPersonAndMember()
            .personalCode(GUARDIAN)
            .role(new Role(PERSON, WARD, "Ward Name"))
            .build();
    authenticate(guardian);

    MockHttpServletRequest request = post("/v1/mandates");
    filter().doFilter(request, response, chain);

    assertThat(chain.getRequest()).isSameAs(request);
    verifyNoInteractions(childRepresentations);
  }

  @Test
  void letsAPersonWithFullCapacityTransact() throws Exception {
    authenticate(ward());
    restrictedCapacity(WARD, false);

    MockHttpServletRequest request = post("/v1/mandates");
    filter().doFilter(request, response, chain);

    assertThat(chain.getRequest()).isSameAs(request);
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void letsThroughThePostsThatAreNotTransactions() throws Exception {
    authenticate(ward());

    MockHttpServletRequest request = post("/v1/t");
    filter().doFilter(request, response, chain);

    assertThat(chain.getRequest()).isSameAs(request);
    verifyNoInteractions(childRepresentations);
  }

  @Test
  void ignoresAnUnauthenticatedRequest() throws Exception {
    MockHttpServletRequest request = post("/v1/mandates");
    filter().doFilter(request, response, chain);

    assertThat(chain.getRequest()).isSameAs(request);
    verifyNoInteractions(childRepresentations);
  }

  private RestrictedCapacityFilter filter() {
    return new RestrictedCapacityFilter(childRepresentations);
  }

  private AuthenticatedPerson ward() {
    return sampleAuthenticatedPersonAndMember()
        .personalCode(WARD)
        .role(new Role(PERSON, WARD, "Ward Name"))
        .build();
  }

  private void authenticate(AuthenticatedPerson person) {
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(person, "token"));
  }

  private void restrictedCapacity(String personalCode, boolean restricted) {
    when(childRepresentations.hasRestrictedLegalCapacity(personalCode)).thenReturn(restricted);
  }

  private MockHttpServletRequest post(String path) {
    return new MockHttpServletRequest("POST", path);
  }
}
