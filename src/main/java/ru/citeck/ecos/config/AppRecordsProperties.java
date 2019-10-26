package ru.citeck.ecos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.records2.RecordsProperties;

@Component
@ConfigurationProperties(prefix = "ecos-gateway.ecos-records")
public class AppRecordsProperties extends RecordsProperties {
}
