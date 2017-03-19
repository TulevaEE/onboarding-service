package ee.tuleva.onboarding.config;

import ee.tuleva.onboarding.auth.AuthUserService;
import ee.tuleva.onboarding.auth.idcard.IdCardTokenGranter;
import ee.tuleva.onboarding.auth.mobileid.MobileIdAuthService;
import ee.tuleva.onboarding.auth.mobileid.MobileIdTokenGranter;
import ee.tuleva.onboarding.auth.session.GenericSessionStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
import org.springframework.security.oauth2.provider.client.JdbcClientDetailsService;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.security.oauth2.provider.token.store.JdbcTokenStore;

import javax.sql.DataSource;

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
                    .regexMatchers("/v1/comparisons.*").permitAll()
                    .regexMatchers("/v1/.*").authenticated()
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
        private AuthUserService authUserService;

        @Autowired
        private GenericSessionStore genericSessionStore;

        @Bean
        public JdbcClientDetailsService clientDetailsService() {
            return new JdbcClientDetailsService(dataSource);
        }

        @Bean
        public TokenStore tokenStore() {
            return new JdbcTokenStore(dataSource);
        }

        @Override
        public void configure(ClientDetailsServiceConfigurer clients) throws Exception {
            clients.withClientDetails(clientDetailsService());
        }

        @Override
        public void configure(AuthorizationServerSecurityConfigurer oauthServer) throws Exception {

        }

        @Override
        public void configure(AuthorizationServerEndpointsConfigurer endpoints) throws Exception {
            MobileIdTokenGranter mobileIdTokenGranter = mobileIdTokenGranter(endpoints);
            IdCardTokenGranter idCardTokenGranter = idCardTokenGranter(endpoints);

            endpoints
                    .tokenStore(tokenStore())
                    .tokenGranter(new CompositeTokenGranter(asList(mobileIdTokenGranter, idCardTokenGranter)));
        }

        private MobileIdTokenGranter mobileIdTokenGranter(AuthorizationServerEndpointsConfigurer endpoints) {
            return new MobileIdTokenGranter(
                            endpoints.getTokenServices(),
                            clientDetailsService(),
                            endpoints.getOAuth2RequestFactory(),
                            mobileIdAuthService,
                            authUserService,
                            genericSessionStore);
        }

        private IdCardTokenGranter idCardTokenGranter(AuthorizationServerEndpointsConfigurer endpoints) {
            return new IdCardTokenGranter(
                    endpoints.getTokenServices(),
                    clientDetailsService(),
                    endpoints.getOAuth2RequestFactory(),
                    genericSessionStore,
                    authUserService);
        }

    }
}