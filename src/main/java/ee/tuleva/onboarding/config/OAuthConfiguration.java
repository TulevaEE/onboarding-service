package ee.tuleva.onboarding.config;

import static ee.tuleva.onboarding.capital.CapitalController.CAPITAL_URI;
import static ee.tuleva.onboarding.config.OAuthConfiguration.ResourceServerPathConfiguration.RESOURCE_REQUEST_MATCHER_BEAN;
import static java.util.Arrays.asList;

import ee.tuleva.onboarding.auth.authority.Authority;
import ee.tuleva.onboarding.auth.authority.GrantedAuthorityFactory;
import ee.tuleva.onboarding.auth.idcard.IdCardTokenGranter;
import ee.tuleva.onboarding.auth.mobileid.MobileIdAuthService;
import ee.tuleva.onboarding.auth.mobileid.MobileIdTokenGranter;
import ee.tuleva.onboarding.auth.principal.PrincipalService;
import ee.tuleva.onboarding.auth.session.GenericSessionStore;
import ee.tuleva.onboarding.auth.smartid.SmartIdAuthService;
import ee.tuleva.onboarding.auth.smartid.SmartIdTokenGranter;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.config.annotation.configurers.ClientDetailsServiceConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configuration.AuthorizationServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableAuthorizationServer;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableResourceServer;
import org.springframework.security.oauth2.config.annotation.web.configuration.ResourceServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerEndpointsConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerSecurityConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configurers.ResourceServerSecurityConfigurer;
import org.springframework.security.oauth2.provider.ClientDetailsService;
import org.springframework.security.oauth2.provider.CompositeTokenGranter;
import org.springframework.security.oauth2.provider.TokenGranter;
import org.springframework.security.oauth2.provider.client.ClientCredentialsTokenGranter;
import org.springframework.security.oauth2.provider.token.AuthorizationServerTokenServices;
import org.springframework.security.oauth2.provider.token.DefaultTokenServices;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.security.oauth2.provider.token.store.JdbcTokenStore;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
public class OAuthConfiguration {

  @Configuration
  public static class ResourceServerPathConfiguration {

    public static final String RESOURCE_REQUEST_MATCHER_BEAN = "resourceServerRequestMatcher";

    @Bean(RESOURCE_REQUEST_MATCHER_BEAN)
    public RequestMatcher resources() {
      return new AntPathRequestMatcher("/v1/**");
    }
  }

  @Configuration
  @EnableResourceServer
  @RequiredArgsConstructor
  protected static class OAuthResourceServerConfig extends ResourceServerConfigurerAdapter {

    private static final String RESOURCE_ID = "onboarding-service";

    @Qualifier(RESOURCE_REQUEST_MATCHER_BEAN)
    final RequestMatcher resources;

    @Override
    public void configure(ResourceServerSecurityConfigurer resources) {
      resources.resourceId(RESOURCE_ID);
    }

    @Override
    public void configure(HttpSecurity http) throws Exception {
      http.requestMatcher(resources)
          .authorizeRequests()
          .regexMatchers("/v1" + CAPITAL_URI)
          .hasAuthority(Authority.MEMBER)
          .regexMatchers(HttpMethod.GET, "/v1/funds.*")
          .hasAnyAuthority(Authority.USER, Authority.ROLE_CLIENT)
          .regexMatchers(HttpMethod.HEAD, "/v1/members")
          .hasAuthority(Authority.ROLE_CLIENT)
          .regexMatchers("/v1/.*")
          .hasAuthority(Authority.USER)
          .and()
          .csrf()
          .ignoringAntMatchers("/v1/**");
    }
  }

  @Configuration
  @EnableAuthorizationServer
  protected static class AuthorizationServerConfiguration
      extends AuthorizationServerConfigurerAdapter {

    @Autowired private DataSource dataSource;

    @Autowired private MobileIdAuthService mobileIdAuthService;

    @Autowired private SmartIdAuthService smartIdAuthService;

    @Autowired private PrincipalService principalService;

    @Autowired private GenericSessionStore genericSessionStore;

    @Autowired private GrantedAuthorityFactory grantedAuthorityFactory;

    @Autowired private AuthenticationManager refreshingAuthenticationManager;

    @Autowired private ApplicationEventPublisher applicationEventPublisher;

    @Autowired private ClientDetailsService clientDetailsService;

    @Bean
    public TokenStore tokenStore() {
      return new JdbcTokenStore(dataSource);
    }

    @Bean
    @Primary
    public AuthorizationServerTokenServices tokenServices() {
      DefaultTokenServices tokenServices = new DefaultTokenServices();
      tokenServices.setTokenStore(tokenStore());
      tokenServices.setAccessTokenValiditySeconds(60 * 30);
      tokenServices.setAuthenticationManager(refreshingAuthenticationManager);
      tokenServices.setClientDetailsService(clientDetailsService);
      return tokenServices;
    }

    @Override
    public void configure(ClientDetailsServiceConfigurer clients) throws Exception {
      clients.jdbc(dataSource);
    }

    @Override
    public void configure(AuthorizationServerSecurityConfigurer oauthServer) {
      oauthServer.checkTokenAccess("hasAuthority('ROLE_TRUSTED_CLIENT')");
    }

    @Override
    public void configure(AuthorizationServerEndpointsConfigurer endpoints) {
      endpoints.tokenServices(tokenServices()).tokenGranter(compositeTokenGranter(endpoints));
    }

    private TokenGranter compositeTokenGranter(AuthorizationServerEndpointsConfigurer endpoints) {
      TokenGranter mobileIdTokenGranter = mobileIdTokenGranter(endpoints);
      TokenGranter smartIdTokenGranter = smartIdTokenGranter(endpoints);
      TokenGranter idCardTokenGranter = idCardTokenGranter(endpoints);
      TokenGranter clientCredentialsTokenGranter =
          new ClientCredentialsTokenGranter(
              endpoints.getTokenServices(),
              clientDetailsService,
              endpoints.getOAuth2RequestFactory());

      return new CompositeTokenGranter(
          asList(
              mobileIdTokenGranter,
              smartIdTokenGranter,
              idCardTokenGranter,
              clientCredentialsTokenGranter));
    }

    private MobileIdTokenGranter mobileIdTokenGranter(
        AuthorizationServerEndpointsConfigurer endpoints) {
      return new MobileIdTokenGranter(
          endpoints.getTokenServices(),
          clientDetailsService,
          endpoints.getOAuth2RequestFactory(),
          mobileIdAuthService,
          principalService,
          genericSessionStore,
          grantedAuthorityFactory,
          applicationEventPublisher);
    }

    private SmartIdTokenGranter smartIdTokenGranter(
        AuthorizationServerEndpointsConfigurer endpoints) {
      return new SmartIdTokenGranter(
          endpoints.getTokenServices(),
          clientDetailsService,
          endpoints.getOAuth2RequestFactory(),
          smartIdAuthService,
          principalService,
          genericSessionStore,
          grantedAuthorityFactory,
          applicationEventPublisher);
    }

    private IdCardTokenGranter idCardTokenGranter(
        AuthorizationServerEndpointsConfigurer endpoints) {
      return new IdCardTokenGranter(
          endpoints.getTokenServices(),
          clientDetailsService,
          endpoints.getOAuth2RequestFactory(),
          genericSessionStore,
          principalService,
          grantedAuthorityFactory,
          applicationEventPublisher);
    }
  }
}
