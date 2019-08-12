package no.fint.linkwalker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class ResourceFetcher {
    private static final double MULTIPLIER = Math.sqrt(2);
    private static final int LIMIT = 10;
    private static final int DELAY = 256;

    @Autowired
    private RestTemplateProvider restTemplateProvider;

    public <T> ResponseEntity<T> fetch(String client, String location, HttpHeaders headers, Class<T> type) {
        int count = 0;
        long delay = DELAY;
        do {
            try {
                ++count;
                return getRestTemplate(client, location).exchange(location, HttpMethod.GET, new HttpEntity<>(headers), type);
            } catch (ResourceAccessException e) {
                log.info(e.getMessage());
                log.info("Retry {}/{} in {} ms ...", count, LIMIT, delay);
                try {
                    TimeUnit.MILLISECONDS.sleep(delay);
                } catch (InterruptedException e1) {
                    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
                }
                delay *= MULTIPLIER;
            }
        } while (count < LIMIT);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }

    private RestTemplate getRestTemplate(String client, String location) {
        if (PwfUtils.isPwf(location)) {
            return restTemplateProvider.getRestTemplate();
        }
        return restTemplateProvider.getAuthRestTemplate(client);
    }

}
