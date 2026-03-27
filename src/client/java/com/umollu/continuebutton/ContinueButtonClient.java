package com.umollu.continuebutton;

import com.google.common.io.Files;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.client.multiplayer.ServerData;
import org.joml.AxisAngle4f;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;

public class ContinueButtonClient implements ClientModInitializer {
	public static boolean lastLocal = true;
	public static String serverName = "";
	public static String serverAddress = "";

	@Override
	public void onInitializeClient() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			if (Minecraft.getInstance().hasSingleplayerServer()) {
                lastLocal = true;
                String levelName = null;
                if (Minecraft.getInstance().getSingleplayerServer() != null) {
                    levelName = Minecraft.getInstance().getSingleplayerServer().getWorldData().getLevelName();
                }
                Path pathToSave = Path.of(Minecraft.getInstance().getSingleplayerServer().getWorldPath(LevelResource.ROOT).toString());
				String folderName = pathToSave.getFileName().toString();
                serverName = levelName;
                serverAddress = folderName;
            } else {
				ServerData serverInfo = Minecraft.getInstance().getCurrentServer();
				lastLocal = false;
				if (serverInfo != null) {
					serverName = serverInfo.name;
					serverAddress = serverInfo.ip;
				}
			}
			saveConfig();
		});	}

	public static void saveConfig() {
		File configDir = new File(FabricLoader.getInstance().getConfigDir().toFile(), "continuebutton");
		File configFile = new File(configDir, "config.properties");
		Properties properties = new Properties();
		try (FileInputStream stream = new FileInputStream(configFile)) {
			properties.load(stream);
		} catch (IOException e) {
		}
		properties.setProperty("last-local", lastLocal? "true" : "false");
		properties.setProperty("server-name", serverName);
		properties.setProperty("server-address", serverAddress);
		try (FileOutputStream stream = new FileOutputStream(configFile)) {
			properties.store(stream, "ContinueButton config");
		} catch (IOException e) {
		}
	}

	static {
		File configDir = new File(FabricLoader.getInstance().getConfigDir().toFile(), "continuebutton");

		if (!configDir.exists()) {
			if (!configDir.mkdir()) {
			}
		}

		File configFile = new File(configDir, "config.properties");
		Properties properties = new Properties();

		if (configFile.exists()) {
			try (FileInputStream stream = new FileInputStream(configFile)) {
				properties.load(stream);
			} catch (IOException e) {
			}
		}

		lastLocal = ((String) properties.computeIfAbsent("last-local", (a) -> "true")).equals("true");
		serverName = ((String) properties.computeIfAbsent("server-name", (a) -> ""));
		serverAddress = ((String) properties.computeIfAbsent("server-address", (a) -> ""));

		try (FileOutputStream stream = new FileOutputStream(configFile)) {
			properties.store(stream, "ContinueButton config");
		} catch (IOException e) {
		}
	}
}