package ee.tuleva.onboarding.config

import com.vladmihalcea.hibernate.type.json.JsonStringType


/**
 Duplicate class on purpose, to use a different json type for tests (h2 database).
 */
public class JsonbType extends JsonStringType {

    private static final long serialVersionUID = -5726604472146143335L

    @Override
    public String getName() {
        return "jsonb"
    }
}