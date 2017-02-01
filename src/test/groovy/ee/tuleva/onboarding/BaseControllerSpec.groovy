package ee.tuleva.onboarding;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import spock.lang.Specification;

public class BaseControllerSpec extends Specification {

    protected final static ObjectMapper mapper = new ObjectMapper()

    protected MockMvc getMockMvc(Object... controllers) {
        MappingJackson2HttpMessageConverter converter = setupJacksonConverter()
        return MockMvcBuilders.standaloneSetup(controllers)
                .setMessageConverters(converter)
                .build()
    }

    protected static MappingJackson2HttpMessageConverter setupJacksonConverter() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(objectMapper);
        return converter
    }

}
