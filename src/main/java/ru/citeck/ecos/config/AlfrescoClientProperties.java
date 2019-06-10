package ru.citeck.ecos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = AlfrescoClientProperties.RIBBON_SERVICE_NAME, ignoreUnknownFields = true)
public class AlfrescoClientProperties {

    public static final String RIBBON_SERVICE_NAME = "alfresco";

    private String schema = "http";

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }
}
