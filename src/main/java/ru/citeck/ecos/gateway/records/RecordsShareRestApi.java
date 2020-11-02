package ru.citeck.ecos.gateway.records;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.citeck.ecos.records3.spring.web.rest.RecordsRestApi;

@RestController
@RequestMapping("/share/api/records")
public class RecordsShareRestApi {

    private final RecordsRestApi restApi;

    @Autowired
    public RecordsShareRestApi(RecordsRestApi restApi) {
        this.restApi = restApi;
    }

    @PostMapping(value = "/query", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public Object recordsQuery(@RequestBody byte[] body) {
        return restApi.recordsQuery(body);
    }

    @PostMapping(value = "/mutate", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public Object recordsMutate(@RequestBody byte[] body) {
        return restApi.recordsMutate(body);
    }

    @PostMapping(value = "/delete", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public Object recordsDelete(@RequestBody byte[] body) {
        return restApi.recordsDelete(body);
    }
}
