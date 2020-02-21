package ru.citeck.ecos.gateway.records;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.citeck.ecos.records2.request.rest.DeletionBody;
import ru.citeck.ecos.records2.request.rest.MutationBody;
import ru.citeck.ecos.records2.request.rest.QueryBody;
import ru.citeck.ecos.records2.spring.web.rest.RecordsRestApi;

@RestController
@RequestMapping("/share/api/records")
public class RecordsShareRestApi {

    private RecordsRestApi restApi;

    @Autowired
    public RecordsShareRestApi(RecordsRestApi restApi) {
        this.restApi = restApi;
    }

    @PostMapping("/query")
    public Object recordsQuery(@RequestBody byte[] body) {
        return restApi.recordsQuery(body);
    }

    @PostMapping("/mutate")
    public Object recordsMutate(@RequestBody byte[] body) {
        return restApi.recordsMutate(body);
    }

    @PostMapping("/delete")
    public Object recordsDelete(@RequestBody byte[] body) {
        return restApi.recordsDelete(body);
    }
}
