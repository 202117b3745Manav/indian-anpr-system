package com.anpr;

import java.io.InputStream;
import java.util.Properties;

public class ConfigLoader {

    private static final Properties properties = new Properties();

    static {
        try (InputStream input = ConfigLoader.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                System.out.println("Sorry, unable to load config.properties");
            } else {
                properties.load(input);
                System.out.println("Configuration loaded successfully.");
            }
        } catch (Exception e) {
            System.out.println("Error loading configuration: " + e.getMessage());
        }
    }

    public static String getProperty(String key) {
        return properties.getProperty(key);
    }

    public static float getFloatProperty(String key, float defaultValue) {
        String value = properties.getProperty(key);
        return value != null ? Float.parseFloat(value) : defaultValue;
    }
}