package ru.citeck.ecos.gateway.records;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriTemplateHandler;
import ru.citeck.ecos.config.AlfrescoClientProperties;
import ru.citeck.ecos.records2.RecordsService;
import ru.citeck.ecos.records2.RecordsServiceFactory;
import ru.citeck.ecos.records2.request.rest.RestHandler;
import ru.citeck.ecos.records2.resolver.RecordsResolver;
import ru.citeck.ecos.records2.resolver.RemoteRecordsResolver;

import javax.net.ssl.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Map;

@Configuration
public class RecordsConfig extends RecordsServiceFactory {

    @Autowired
    private RestTemplateBuilder restTemplateBuilder;
    @Autowired
    private LanguageRelayingInterceptor languageRelayingInterceptor;
    @Autowired
    private CookiesRelayingInterceptor cookiesRelayingInterceptor;
    @Autowired
    private AlfrescoClientProperties alfrescoClientProperties;

    @Autowired
    @Qualifier("alfrescoRestTemplate")
    private RestTemplate alfrescoRestTemplate;

    @Autowired
    @Qualifier("microservicesRestTemplate")
    private RestTemplate microservicesRestTemplate;

    private static final String DEFAULT_APP = "alfresco";

    @Bean
    public RecordsService createRecordsService() {
        return super.createRecordsService();
    }

    private <T> T jsonPost(String url, Object req, Class<T> respType) {

        if (url.contains("/alfresco")) {
            return alfrescoRestTemplate.postForObject(url, req, respType);
        }

        return microservicesRestTemplate.postForObject(url, req, respType);
    }

    @Override
    public RecordsResolver createRecordsResolver() {
        RemoteRecordsResolver resolver = new RemoteRecordsResolver(this::jsonPost);
        resolver.setDefaultAppName(DEFAULT_APP);
        return resolver;
    }

    @Bean
    public RestHandler createRestHandler(RecordsService recordsService) {
        return new RestHandler(recordsService);
    }

    @Component
    public static class LanguageRelayingInterceptor implements ClientHttpRequestInterceptor {
        @Autowired(required = false)
        private HttpServletRequest thisRequest;

        @Override
        public ClientHttpResponse intercept(HttpRequest newRequest, byte[] bytes, ClientHttpRequestExecution clientHttpRequestExecution) throws IOException {
            if (thisRequest != null) {
                newRequest.getHeaders().set("Accept-Language",
                    thisRequest.getHeader("Accept-Language"));
            }
            return clientHttpRequestExecution.execute(newRequest, bytes);
        }
    }

    @Component
    //Relays ALL received cookies.
    //We could only relay JSESSIONID cookie - but, given how record-searching endpoint is served by Alfresco
    //  and its authentication is managed by alfresco UI cookies, it's bad for out microservice to
    //  know which cookies exactly are responsible for auth. On the other hand, while usually it's
    //  insecure to expose all client's cookies sent to us to some another external service, in this scenario the
    //  external service is not unrelated to our client, as both UI and records-search endpoints are served by alfresco.
    //todo However the whole solution still smells and is temporary, until we are able check access to records
    //  without asking alfresco, which is expected to be possible soon. That will allow us to just call records-service
    //  with admin user or maybe microservice auth (if records-search service becomes a microservice),
    //  and either post-filter returned records or specify username to test access against.
    public static class CookiesRelayingInterceptor implements ClientHttpRequestInterceptor {
        @Autowired(required = false)
        private HttpServletRequest thisRequest;

        @Override
        public ClientHttpResponse intercept(HttpRequest newRequest, byte[] bytes, ClientHttpRequestExecution clientHttpRequestExecution) throws IOException {
            if (thisRequest != null) {
                newRequest.getHeaders().set("Cookie",
                    thisRequest.getHeader("Cookie"));
            }
            return clientHttpRequestExecution.execute(newRequest, bytes);
        }
    }

    @LoadBalanced
    @Bean
    public RestTemplate alfrescoRestTemplate() {
        return restTemplateBuilder
            .requestFactory(SkipSslVerificationHttpRequestFactory.class)
            //todo this replacement with hardcoded values is just until /api/ecos/records is working,
            //which is expected quite soon
            .uriTemplateHandler(new UriTemplateHandler() {
                UriTemplateHandler deflt = new DefaultUriBuilderFactory();

                @Override
                public URI expand(String s, Map<String, ?> map) {
                    s = s.replace("alfresco/api/records/", "/share/proxy/alfresco/citeck/ecos/records/");
                    return deflt.expand(s, map);
                }

                @Override
                public URI expand(String s, Object... objects) {
                    s = s.replace("alfresco/api/records/", "/share/proxy/alfresco/citeck/ecos/records/");
                    return deflt.expand(s, objects);
                }
            })
            .additionalInterceptors(languageRelayingInterceptor, cookiesRelayingInterceptor)
            .rootUri(alfrescoClientProperties.getSchema() + "://" + AlfrescoClientProperties.RIBBON_SERVICE_NAME)
            .build();
    }

    @LoadBalanced
    @Bean
    public RestTemplate microservicesRestTemplate() {
        return restTemplateBuilder
            .requestFactory(SkipSslVerificationHttpRequestFactory.class)
            .additionalInterceptors(languageRelayingInterceptor, cookiesRelayingInterceptor)
            .rootUri("http://gateway")
            .build();
    }

    //Basically copied from org.springframework.boot.actuate.autoconfigure.cloudfoundry.servlet.SkipSslVerificationHttpRequestFactory
    private static class SkipSslVerificationHttpRequestFactory extends SimpleClientHttpRequestFactory {
        @Override
        protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
            if (connection instanceof HttpsURLConnection) {
                try {
                    ((HttpsURLConnection) connection).setHostnameVerifier(
                        (String s, SSLSession sslSession) -> true);
                    ((HttpsURLConnection) connection).setSSLSocketFactory(
                        this.createSslSocketFactory());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            super.prepareConnection(connection, httpMethod);
        }

        private SSLSocketFactory createSslSocketFactory() throws Exception {
            final SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[]{new SkipX509TrustManager()}, new SecureRandom());
            return context.getSocketFactory();
        }

        private static class SkipX509TrustManager implements X509TrustManager {
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }

            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }
        }
    }
}
