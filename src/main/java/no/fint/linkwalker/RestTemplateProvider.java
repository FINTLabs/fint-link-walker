package no.fint.linkwalker;

import no.fint.oauth.OAuthRestTemplateFactory;
import no.fint.portal.model.client.Client;
import no.fint.portal.model.client.ClientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;

@Service
public class RestTemplateProvider {

    @Autowired
    private ClientService clientService;

    @Autowired
    private OAuthRestTemplateFactory oAuthRestTemplateFactory;

    private final ConcurrentMap<String, RestTemplate> restTemplateCache = new ConcurrentSkipListMap<>();

    public RestTemplate getRestTemplate() {
        return withPermissiveErrorHandler(new RestTemplate());
    }

    public RestTemplate getAuthRestTemplate(String client) {
        return restTemplateCache.computeIfAbsent(client, this::createAuthRestTemplate);
    }

    private RestTemplate createAuthRestTemplate(String clientDn) {
        Client client = clientService.getClientByDn(clientDn).orElseThrow(SecurityException::new);
        String password = UUID.randomUUID().toString().toLowerCase();
        clientService.resetClientPassword(client, password);
        String clientSecret = clientService.getClientSecret(client);

        return withPermissiveErrorHandler(oAuthRestTemplateFactory.create(client.getName(), password, client.getClientId(), clientSecret));
    }

    private RestTemplate withPermissiveErrorHandler(RestTemplate input) {
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
