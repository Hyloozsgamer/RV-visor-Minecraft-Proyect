package com.rvvisor.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.rvvisor.RVVisorMod;
import com.rvvisor.core.optics.LensSettings;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Path;

/**
 * Persistent Configuration Manager for RV-Visor.
 * Automatically loads and saves user settings (Render Scale, MSAA, CAS Sharpness)
 * to config/rvvisor.json so changes persist across game restarts.
 */
public class VRConfigFile {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE_NAME = "rvvisor.json";

    private static Path getConfigPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE_NAME);
    }

    public static void load(LensSettings settings) {
        if (settings == null) return;
        File file = getConfigPath().toFile();
        if (!file.exists()) {
            // Save initial defaults if no config exists (Default CAS Sharpness = OFF / 0.0)
            save(settings);
            return;
        }

        try (FileReader reader = new FileReader(file)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json != null) {
                if (json.has("renderScale")) {
                    settings.setSupersamplingScale(json.get("renderScale").getAsFloat());
                }
                if (json.has("msaaSamples")) {
                    settings.setMsaaSamples(json.get("msaaSamples").getAsInt());
                }
                if (json.has("sharpness")) {
                    settings.setSharpness(json.get("sharpness").getAsFloat());
                }
                if (json.has("dynamicResolution")) {
                    settings.setDynamicResolutionEnabled(json.get("dynamicResolution").getAsBoolean());
                }
                RVVisorMod.LOGGER.info("[RV-Visor] Config loaded successfully from {}: scale={}x, msaa={}x, CAS={}",
                        CONFIG_FILE_NAME, settings.getSupersamplingScale(), settings.getMsaaSamples(), settings.getSharpness());
            }
        } catch (Throwable t) {
            RVVisorMod.LOGGER.error("[RV-Visor] Failed to load config from {}", CONFIG_FILE_NAME, t);
        }
    }

    public static void save(LensSettings settings) {
        if (settings == null) return;
        try {
            File file = getConfigPath().toFile();
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            JsonObject json = new JsonObject();
            json.addProperty("renderScale", settings.getSupersamplingScale());
            json.addProperty("msaaSamples", settings.getMsaaSamples());
            json.addProperty("sharpness", settings.getSharpness());
            json.addProperty("dynamicResolution", settings.isDynamicResolutionEnabled());

            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(json, writer);
            }
            RVVisorMod.LOGGER.info("[RV-Visor] Config saved to {}: scale={}x, msaa={}x, CAS={}",
                    CONFIG_FILE_NAME, settings.getSupersamplingScale(), settings.getMsaaSamples(), settings.getSharpness());
        } catch (Throwable t) {
            RVVisorMod.LOGGER.error("[RV-Visor] Failed to save config to {}", CONFIG_FILE_NAME, t);
        }
    }
}
