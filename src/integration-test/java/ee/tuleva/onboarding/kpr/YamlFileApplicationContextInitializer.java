package ee.tuleva.onboarding.kpr;

import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.Resource;

import java.io.IOException;

/**
 * http://stackoverflow.com/questions/21271468/spring-propertysource-using-yaml
 */
public class YamlFileApplicationContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        try {
            Resource resource = applicationContext.getResource("classpath:application.yml");
            YamlPropertySourceLoader sourceLoader = new YamlPropertySourceLoader();
            PropertySource<?> yamlTestProperties = yamlTestProperties = sourceLoader.load("yamlTestProperties", resource, null);
            applicationContext.getEnvironment().getPropertySources().addFirst(yamlTestProperties);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
