package no.fint.linkwalker.service;

import lombok.extern.slf4j.Slf4j;
import no.fint.linkwalker.RestTemplateProvider;
import no.fint.oauth.OAuthRestTemplateFactory;
import no.fint.portal.config.LdapConfiguration;
import no.fint.portal.model.client.Client;
import no.fint.portal.model.client.ClientService;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.OAuth2RestTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;

@Service
@Slf4j
@ConditionalOnProperty(name = "fint.rest-template.provider", havingValue = "portal-api")
@Import(LdapConfiguration.class)
@ComponentScan(basePackages = "no.fint.portal")
public class PortalApiRestTemplateProvider extends RestTemplateProvider {

    @Autowired
    private ClientService clientService;

    @Autowired
    private OAuthRestTemplateFactory oAuthRestTemplateFactory;

    private final ConcurrentMap<Pair<String, String>, OAuth2RestTemplate> restTemplateCache = new ConcurrentSkipListMap<>();

    @PostConstruct
    public void init() {
        log.info("Authorization using Portal API enabled.");
    }

    @Override
    public RestTemplate getRestTemplate() {
        return withPermissiveErrorHandler(new RestTemplate());
    }

    @Override
    public RestTemplate getAuthRestTemplate(String organisation, String client) {
        return withPermissiveErrorHandler(restTemplateCache.compute(Pair.of(client, organisation), this::computeOAuthRestTemplate));
    }

    private OAuth2RestTemplate computeOAuthRestTemplate(Pair<String,String> key, OAuth2RestTemplate restTemplate) {
        if (restTemplate == null
                || restTemplate.getOAuth2ClientContext() == null
                || restTemplate.getOAuth2ClientContext().getAccessToken() == null
                || restTemplate.getOAuth2ClientContext().getAccessToken().isExpired()) {
            return createOAuthRestTemplate(key.getLeft(), key.getRight());
        }
        return restTemplate;
    }

    private OAuth2RestTemplate createOAuthRestTemplate(String clientName, String organisationName) {
        Client client = clientService.getClient(clientName, organisationName).orElseThrow(SecurityException::new);
        String password = UUID.randomUUID().toString().toLowerCase();
        clientService.resetClientPassword(client, password);
        String clientSecret = clientService.getClientSecret(client);

        return oAuthRestTemplateFactory.create(client.getName(), password, client.getClientId(), clientSecret);
    }

}
