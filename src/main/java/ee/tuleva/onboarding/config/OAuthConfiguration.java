package ee.tuleva.onboarding.config;

import ee.tuleva.onboarding.auth.authority.Authority;
import ee.tuleva.onboarding.auth.authority.GrantedAuthorityFactory;
import ee.tuleva.onboarding.auth.idcard.IdCardTokenGranter;
import ee.tuleva.onboarding.auth.mobileid.MobileIdAuthService;
import ee.tuleva.onboarding.auth.mobileid.MobileIdTokenGranter;
import ee.tuleva.onboarding.auth.principal.PrincipalService;
import ee.tuleva.onboarding.auth.session.GenericSessionStore;
import org.springframework.beans.factory.annotation.Autowired;
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
import org.springframework.security.oauth2.provider.CompositeTokenGranter;
import org.springframework.security.oauth2.provider.TokenGranter;
import org.springframework.security.oauth2.provider.client.ClientCredentialsTokenGranter;
import org.springframework.security.oauth2.provider.client.JdbcClientDetailsService;
import org.springframework.security.oauth2.provider.refresh.RefreshTokenGranter;
import org.springframework.security.oauth2.provider.token.AuthorizationServerTokenServices;
import org.springframework.security.oauth2.provider.token.DefaultTokenServices;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.security.oauth2.provider.token.store.JdbcTokenStore;

import javax.sql.DataSource;

import static ee.tuleva.onboarding.capital.InitialCapitalController.INITIAL_CAPITAL_URI;
import static ee.tuleva.onboarding.mandate.MandateController.MANDATES_URI;
import static java.util.Arrays.asList;

@Configuration
public class OAuthConfiguration {

    @Configuration
    @EnableResourceServer
    protected static class OAuthResourceServerConfig extends ResourceServerConfigurerAdapter {

        private static final String RESOURCE_ID = "onboarding-service";

        @Override
        public void configure(ResourceServerSecurityConfigurer resources) {
            resources.resourceId(RESOURCE_ID);
        }

        @Override
        public void configure(HttpSecurity http) throws Exception {
            http
                    .authorizeRequests()
                    .regexMatchers("/v1" + INITIAL_CAPITAL_URI).hasAuthority(Authority.MEMBER)
                    .regexMatchers(HttpMethod.GET, "/v1/funds.*").hasAnyAuthority(Authority.USER, Authority.ROLE_CLIENT)
                    .regexMatchers(HttpMethod.POST, "/v1/users").hasAuthority(Authority.ROLE_CLIENT)
                    .regexMatchers(HttpMethod.HEAD, "/v1/members").hasAuthority(Authority.ROLE_CLIENT)
                    .regexMatchers("/v1/.*").hasAuthority(Authority.USER)
                    ;
        }
    }

    @Configuration
    @EnableAuthorizationServer
    protected static class AuthorizationServerConfiguration extends AuthorizationServerConfigurerAdapter {

        @Autowired
        private DataSource dataSource;

        @Autowired
        private MobileIdAuthService mobileIdAuthService;

        @Autowired
        private PrincipalService principalService;

        @Autowired
        private GenericSessionStore genericSessionStore;

        @Autowired
        private GrantedAuthorityFactory grantedAuthorityFactory;

        @Autowired
        private AuthenticationManager refreshingAuthenticationManager;

        @Autowired
        private ApplicationEventPublisher applicationEventPublisher;

        @Bean
        public JdbcClientDetailsService clientDetailsService() {
            return new JdbcClientDetailsService(dataSource);
        }

        @Bean
        public TokenStore tokenStore() {
            return new JdbcTokenStore(dataSource);
        }

        @Bean
        @Primary
        public AuthorizationServerTokenServices tokenServices() {
            DefaultTokenServices tokenServices = new DefaultTokenServices();
            tokenServices.setTokenStore(tokenStore());
            tokenServices.setSupportRefreshToken(true);
            tokenServices.setReuseRefreshToken(false);
            tokenServices.setAuthenticationManager(refreshingAuthenticationManager);
            tokenServices.setClientDetailsService(clientDetailsService());
            return tokenServices;
        }

        @Override
        public void configure(ClientDetailsServiceConfigurer clients) throws Exception {
            clients.withClientDetails(clientDetailsService());
        }

        @Override
        public void configure(AuthorizationServerSecurityConfigurer oauthServer) throws Exception {
            oauthServer.checkTokenAccess("hasAuthority('ROLE_TRUSTED_CLIENT')");
        }

        @Override
        public void configure(AuthorizationServerEndpointsConfigurer endpoints) throws Exception {
            endpoints
                    .tokenServices(tokenServices())
                    .tokenGranter(compositeTokenGranter(endpoints));

        }

        private TokenGranter compositeTokenGranter(AuthorizationServerEndpointsConfigurer endpoints) {
            TokenGranter mobileIdTokenGranter = mobileIdTokenGranter(endpoints);
            TokenGranter idCardTokenGranter = idCardTokenGranter(endpoints);
            TokenGranter refreshTokenGranter = new RefreshTokenGranter(
              endpoints.getTokenServices(), clientDetailsService(), endpoints.getOAuth2RequestFactory());
            TokenGranter clientCredentialsTokenGranter =
                    new ClientCredentialsTokenGranter(
                            endpoints.getTokenServices(), clientDetailsService(), endpoints.getOAuth2RequestFactory());

            return new CompositeTokenGranter(asList(mobileIdTokenGranter, idCardTokenGranter,
              refreshTokenGranter, clientCredentialsTokenGranter));
        }

        private MobileIdTokenGranter mobileIdTokenGranter(AuthorizationServerEndpointsConfigurer endpoints) {
            return new MobileIdTokenGranter(
                            endpoints.getTokenServices(),
                            clientDetailsService(),
                            endpoints.getOAuth2RequestFactory(),
                            mobileIdAuthService,
                            principalService,
                            genericSessionStore,
                            grantedAuthorityFactory,
                            applicationEventPublisher);
        }

        private IdCardTokenGranter idCardTokenGranter(AuthorizationServerEndpointsConfigurer endpoints) {
            return new IdCardTokenGranter(
                    endpoints.getTokenServices(),
                    clientDetailsService(),
                    endpoints.getOAuth2RequestFactory(),
                    genericSessionStore,
                    principalService,
                    grantedAuthorityFactory,
                    applicationEventPublisher);
        }

    }
}