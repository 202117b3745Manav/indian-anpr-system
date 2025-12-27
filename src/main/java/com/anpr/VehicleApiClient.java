package com.anpr;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class VehicleApiClient {

    private static final Logger logger = LoggerFactory.getLogger(VehicleApiClient.class);
    private static final Gson gson = new Gson();
    private static String apiUsername;
    private static String apiUrl;

    static {
        try (InputStream input = VehicleApiClient.class.getClassLoader().getResourceAsStream("config.properties")) {
            Properties prop = new Properties();
            if (input != null) {
                prop.load(input);
                apiUsername = prop.getProperty("api.username");
                apiUrl = prop.getProperty("api.url");
            }
        } catch (Exception ex) {
            logger.error("Error loading config", ex);
        }
    }

    /**
     * Real implementation: Calls the RegCheck API.
     * @param plateNumber The license plate number to look up.
     * @return A VehicleDetails object or null if not found.
     */
    public static VehicleDetails fetchVehicleDetails(String plateNumber) {
        logger.info("Fetching details for {}...", plateNumber);
        
        if (apiUsername == null || apiUrl == null) {
            logger.error("API configuration missing. Please check config.properties.");
            return null;
        }

        try {
            // Prepare Form Data
            String formData = "RegistrationNumber=" + URLEncoder.encode(plateNumber, StandardCharsets.UTF_8)
                    + "&username=" + URLEncoder.encode(apiUsername, StandardCharsets.UTF_8);

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formData))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return parseApiResponse(response.body());
            } else {
                logger.error("API Error: Received status code {}", response.statusCode());
            }
        } catch (Exception e) {
            logger.error("Error fetching vehicle details for {}", plateNumber, e);
        }
        return null;
    }

    private static VehicleDetails parseApiResponse(String xmlResponse) {
        try {
            // 1. Parse XML to get the inner JSON string from <vehicleJson> tag
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xmlResponse)));
            
            // Extract the JSON string
            String jsonString = doc.getElementsByTagName("vehicleJson").item(0).getTextContent();

            if (jsonString == null || jsonString.isEmpty()) {
                return null;
            }

            // 2. Parse the JSON data from the API
            JsonObject apiJson = JsonParser.parseString(jsonString).getAsJsonObject();

            // 3. Map API fields to our VehicleDetails expected format
            // API returns: "Owner", "Description" (Model), "RegistrationDate"
            // We map to: "ownerName", "vehicleModel", "registrationDate"
            
            JsonObject mappedJson = new JsonObject();
            
            if (apiJson.has("Owner")) {
                mappedJson.addProperty("ownerName", apiJson.get("Owner").getAsString());
            }
            if (apiJson.has("Description")) {
                mappedJson.addProperty("vehicleModel", apiJson.get("Description").getAsString());
            }
            if (apiJson.has("RegistrationDate")) {
                mappedJson.addProperty("registrationDate", apiJson.get("RegistrationDate").getAsString());
            }

            // Convert mapped JSON to VehicleDetails object
            return gson.fromJson(mappedJson, VehicleDetails.class);

        } catch (Exception e) {
            logger.error("Error parsing API response", e);
            return null;
        }
    }
}