package com.krielwus.webtracinganalysis.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> map = new HashMap<>();
        File f = new File(".env");
        if (f.exists() && f.isFile()) {
            try {
                List<String> lines = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
                for (String line : lines) {
                    String s = line.trim();
                    if (s.isEmpty()) continue;
                    if (s.startsWith("#")) continue;
                    if (s.startsWith("export ")) s = s.substring(7).trim();
                    int idx = s.indexOf('=');
                    if (idx < 0) continue;
                    String key = s.substring(0, idx).trim();
                    String val = s.substring(idx + 1).trim();
                    if ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'"))) {
                        val = val.substring(1, val.length() - 1);
                    }
                    map.put(key, val);
                }
            } catch (Exception ignored) {
            }
        }
        if (!map.isEmpty()) {
            MutablePropertySources sources = environment.getPropertySources();
            MapPropertySource ps = new MapPropertySource("dotenv", map);
            if (sources.contains("systemEnvironment")) {
                sources.addAfter("systemEnvironment", ps);
            } else {
                sources.addFirst(ps);
            }
        }
    }
}

