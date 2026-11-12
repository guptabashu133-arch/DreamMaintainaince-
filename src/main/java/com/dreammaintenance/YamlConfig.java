package com.dreammaintenance;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

/** Minimal YAML config: user file values with fallback to the defaults bundled in the jar. */
final class YamlConfig {

    private final Map<String, Object> values;
    private final Map<String, Object> defaults;

    private YamlConfig(Map<String, Object> values, Map<String, Object> defaults) {
        this.values = values;
        this.defaults = defaults;
    }

    static YamlConfig load(Path file, String resource) throws IOException {
        Files.createDirectories(file.getParent());
        if (Files.notExists(file)) {
            try (InputStream in = YamlConfig.class.getClassLoader().getResourceAsStream(resource)) {
                if (in == null) {
                    throw new IOException("Missing resource " + resource);
                }
                Files.copy(in, file);
            }
        }
        Map<String, Object> defaults;
        try (InputStream in = YamlConfig.class.getClassLoader().getResourceAsStream(resource)) {
            defaults = parse(in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        Map<String, Object> values;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            values = asMap(new Yaml().load(reader));
        }
        return new YamlConfig(values, defaults);
    }

    private static Map<String, Object> parse(String text) {
        if (text == null) {
            return Collections.emptyMap();
        }
        return asMap(new Yaml().load(text));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : Collections.emptyMap();
    }

    private static Object find(Map<String, Object> root, String path) {
        Object current = root;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(part);
        }
        return current;
    }

    Object get(String path) {
        Object v = find(values, path);
        return v != null ? v : find(defaults, path);
    }

    String getString(String path, String def) {
        Object v = get(path);
        return v == null ? def : String.valueOf(v);
    }

    int getInt(String path, int def) {
        Object v = get(path);
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return v == null ? def : Integer.parseInt(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    boolean getBoolean(String path, boolean def) {
        Object v = get(path);
        if (v instanceof Boolean b) {
            return b;
        }
        return v == null ? def : Boolean.parseBoolean(String.valueOf(v));
    }

    /** Keys of a section, in file order (user file if it has the section, otherwise defaults). */
    List<String> getKeys(String path) {
        Object v = find(values, path);
        if (!(v instanceof Map<?, ?>)) {
            v = find(defaults, path);
        }
        List<String> out = new ArrayList<>();
        if (v instanceof Map<?, ?> map) {
            map.keySet().forEach(k -> out.add(String.valueOf(k)));
        }
        return out;
    }

    List<String> getStringList(String path) {
        Object v = get(path);
        List<String> out = new ArrayList<>();
        if (v instanceof List<?> list) {
            for (Object o : list) {
                if (o != null) {
                    out.add(String.valueOf(o));
                }
            }
        }
        return out;
    }
}
