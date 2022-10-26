package no.fint.linkwalker;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import no.fint.oauth.OAuthConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.annotation.PostConstruct;

@Import(OAuthConfig.class)
@EnableAsync
@EnableScheduling
@SpringBootApplication
public class Application {

    @Autowired
    private ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        objectMapper.enable(MapperFeature.DEFAULT_VIEW_INCLUSION);
    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
