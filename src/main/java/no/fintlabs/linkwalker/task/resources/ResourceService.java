package no.fintlabs.linkwalker.task.resources;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Service
public class ResourceService {

	@Value("${fint.baseUrl}")
	private String baseUrl;

	public List<String> getResources(String domainAndComponent) throws JSONException {
		List<String> urls = new ArrayList<>();
		RestTemplate restTemplate = new RestTemplate();
		String url = baseUrl + domainAndComponent;

		String response = restTemplate.getForObject(url, String.class);

		JSONObject jsonObject = new JSONObject(response);

		for (Iterator it = jsonObject.keys(); it.hasNext(); ) {
			String key = (String) it.next();
			JSONObject innerObject = jsonObject.getJSONObject(key);
			urls.add(innerObject.getString("collectionUrl"));
		}
		return findResources(urls);
	}

	public List<String> findResources(List<String> urls){
		List<String> resousces = new ArrayList<>();
		urls.forEach(s -> {
			resousces.add(s.substring(s.lastIndexOf("/")));
		});
		return resousces;
	}
}
