package ee.tuleva.onboarding.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;
import java.io.IOException;
import java.util.Map;

@Converter(autoApply = true)
public class MapJsonConverter implements AttributeConverter<Map, String> {

    @Override
    public String convertToDatabaseColumn(Map entityValue) {
        if( entityValue == null )
            return null;

        ObjectMapper mapper = new ObjectMapper();

        try {
            return mapper.writeValueAsString(entityValue);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public Map<String, Object> convertToEntityAttribute(String databaseValue) {
        if( databaseValue == null )
            return null;

        ObjectMapper mapper = new ObjectMapper();

        try {
            return mapper.readValue(databaseValue, Map.class);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
