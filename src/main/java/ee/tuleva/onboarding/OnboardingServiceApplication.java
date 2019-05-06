package ee.tuleva.onboarding;

import io.sentry.Sentry;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.info.GitProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Profiles;

@SpringBootApplication
@Slf4j
public class OnboardingServiceApplication {

    public static void main(String[] args) {
        // CloudFlare is not a fan of Java user agents
        System.setProperty("http.agent", "HTTPie/1.0.2");
        val context = SpringApplication.run(OnboardingServiceApplication.class, args);
        trackRelease(context);
    }

    private static void trackRelease(ApplicationContext context) {
        try {
            val gitProperties = context.getBean(GitProperties.class);
            Sentry.getStoredClient().setRelease(gitProperties.getShortCommitId());
            val isProduction = context.getEnvironment().acceptsProfiles(Profiles.of("production"));
            if (isProduction) {
                Sentry.getStoredClient().setEnvironment("production");
            }
        } catch (NoSuchBeanDefinitionException e) {
            log.info("No git properties found, continuing without release info");
        }
    }

}