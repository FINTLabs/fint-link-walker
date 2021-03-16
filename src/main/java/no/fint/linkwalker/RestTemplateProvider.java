package no.fint.linkwalker;

import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

public abstract class RestTemplateProvider {
    public abstract RestTemplate getRestTemplate();

    public abstract RestTemplate getAuthRestTemplate(String organisation, String client);

    protected RestTemplate withPermissiveErrorHandler(RestTemplate input) {
        input.setErrorHandler(new ResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }

            @Override
            public void handleError(ClientHttpResponse response) {
                return;
            }
        });

        return input;
    }
}
