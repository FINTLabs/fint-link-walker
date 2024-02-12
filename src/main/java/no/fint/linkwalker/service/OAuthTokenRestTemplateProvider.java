package no.fint.linkwalker.service;

import lombok.extern.slf4j.Slf4j;
import no.fint.linkwalker.RestTemplateProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.client.OAuth2RestTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;


@Service
@Slf4j
@ConditionalOnProperty(name = "fint.rest-template.provider", havingValue = "oauth", matchIfMissing = true)
public class OAuthTokenRestTemplateProvider extends RestTemplateProvider {

    @Autowired
    private OAuth2RestTemplate restTemplate;

    @PostConstruct
    public void init() {
        log.info("Authorization using Resource Owner credentials enabled.");
    }

    @Override
    public RestTemplate getRestTemplate() {
        return withPermissiveErrorHandler(new RestTemplate());
    }

    @Override
    public RestTemplate getAuthRestTemplate(String organisation, String client) {
        return withPermissiveErrorHandler(restTemplate);
    }
}
