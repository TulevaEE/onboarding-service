package ee.tuleva.onboarding.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;
import java.io.IOException;
import java.util.Map;

@Converter(autoApply = true)
@Slf4j
public class MapJsonConverter implements AttributeConverter<Map<String, Object>, String> {

    private ObjectMapper mapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(Map entityValue) {
        if (entityValue == null) {
            return null;
        }

        try {
            return mapper.writeValueAsString(entityValue);
        } catch (JsonProcessingException e) {
            log.error("JSON writing error", e);
        }

        return null;
    }

    @Override
    public Map<String, Object> convertToEntityAttribute(String databaseValue) {
        if (databaseValue == null) {
            return null;
        }

        try {
            return mapper.readValue(databaseValue, Map.class);
        } catch (IOException e) {
            log.error("JSON reading error", e);
        }

        return null;
    }
}
