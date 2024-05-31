package no.fintlabs.linkwalker.task.resources;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.*;

@Service
public class ResourceService {

	@Value("${fint.metaModel}")
	private String url;

	private final RestTemplate restTemplate = new RestTemplate();

	@Getter
	private Map<String, List<String>> resultMap = new HashMap<>();

	@PostConstruct
	public void getResources() {
		ObjectMapper objectMapper = new ObjectMapper();

		String response = restTemplate.getForObject(url, String.class);

		try {
			JsonNode rootNode = objectMapper.readTree(response);


			JsonNode entriesNode = rootNode.path("_embedded").path("_entries");

			Iterator<JsonNode> elements = entriesNode.elements();
			while (elements.hasNext()) {
				JsonNode entry = elements.next();

				String id = entry.path("id").path("identifikatorverdi").asText();

				String[] parts = id.split("\\.");
				String key = parts[2] + " " + parts[3];
				String value = parts[parts.length - 1];

				resultMap.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
