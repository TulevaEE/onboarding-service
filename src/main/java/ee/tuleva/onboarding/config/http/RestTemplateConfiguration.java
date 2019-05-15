package ee.tuleva.onboarding.config.http;

import de.codecentric.boot.admin.client.config.ClientProperties;
import ee.tuleva.onboarding.error.RestResponseErrorHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.Arrays;

@Configuration
@Slf4j
public class RestTemplateConfiguration {

    @Bean
    @Primary
    RestTemplate restTemplate(RestTemplateBuilder restTemplateBuilder, RestResponseErrorHandler errorHandler) {
        return restTemplateBuilder
            .errorHandler(errorHandler)
            .setConnectTimeout(Duration.ofSeconds(60))
            .setReadTimeout(Duration.ofSeconds(60))
            .build();
    }

    @Bean
    RestTemplateCustomizer loggingRestTemplateCustomizer
        (@Autowired(required = false) ClientProperties clientProperties) {
        return restTemplate -> {
            SimpleClientHttpRequestFactory simpleClient = new SimpleClientHttpRequestFactory();
            simpleClient.setOutputStreaming(false);
            restTemplate.setRequestFactory(new BufferingClientHttpRequestFactory(simpleClient));
            restTemplate.getInterceptors().add((request, body, execution) -> {
                if (isAdminUrl(clientProperties, request.getURI().getHost())) {
                    log.debug("Not logging requests to admin URL {}", request.getURI());
                    return execution.execute(request, body);
                }
                log.info("Sending request to {} \n {}", request.getURI(), new String(body));
                ClientHttpResponse response = execution.execute(request, body);
                log.info("Response status {} and body \n {}",
                    response.getStatusCode(),
                    IOUtils.toString(response.getBody(), "UTF-8"));
                return response;
            });
        };
    }

    private boolean isAdminUrl(ClientProperties clientProperties, String host) {
        if (clientProperties == null) {
            return false;
        }
        return Arrays.stream(clientProperties.getUrl()).anyMatch(it -> {
            try {
                return new URL(it).getHost().equalsIgnoreCase(host);
            } catch (MalformedURLException e) {
                return false;
            }
        });
    }

}
