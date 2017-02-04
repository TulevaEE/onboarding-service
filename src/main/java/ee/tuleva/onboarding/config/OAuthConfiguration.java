package ee.tuleva.onboarding.config;

import ee.tuleva.onboarding.auth.MobileIdAuthService;
import ee.tuleva.onboarding.auth.MobileIdSessionStore;
import ee.tuleva.onboarding.auth.MobileIdTokenGranter;
import ee.tuleva.onboarding.user.UserRepository;
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
import org.springframework.security.oauth2.provider.client.JdbcClientDetailsService;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.security.oauth2.provider.token.store.JdbcTokenStore;

import javax.sql.DataSource;

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
                    .anonymous().and()
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
        private UserRepository userRepository;

        @Autowired
        private MobileIdSessionStore mobileIdSessionStore;

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
            MobileIdTokenGranter mobileIdTokenGranter = new MobileIdTokenGranter(
                    endpoints.getTokenServices(),
                    clientDetailsService(),
                    endpoints.getOAuth2RequestFactory(),
                    mobileIdAuthService,
                    userRepository,
                    mobileIdSessionStore
            );

            endpoints
                    .tokenStore(tokenStore())
                    .tokenGranter(mobileIdTokenGranter)
            ;
        }

    }
}