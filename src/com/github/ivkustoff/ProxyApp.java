package com.github.ivkustoff;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProxyApp {
    private Config config;

    public void parseConfig() {
        final ClassLoader classLoader = new ProxyApp().getClass().getClassLoader();
        final URL resource = classLoader.getResource("resources/config.properties");
        if (resource == null) {
            System.out.println("config.properties not found in resource folder");
        } else {
            final Path configFile = Paths.get(new File(resource.getFile()).getAbsolutePath());
            config = new Config(configFile);
            final List<String> parsingMessages = config.parseConfig();
            if (config.isCorrectConfig()) {
                System.out.println("Incorrect config.properties file");
            }
            if (!parsingMessages.isEmpty()) {
                System.out.println("Config parsing results:");
                for (String message : parsingMessages) {
                    System.out.println(message);
                }
            }
        }
    }

    public static void main(String[] args) {
        ProxyApp app = new ProxyApp();
        app.parseConfig();
        app.start();

    }

    private void start() {
        if (config.isCorrectConfig()) {
            int amountOfThreads = Math.min(Runtime.getRuntime().availableProcessors(), config.size());
            final ExecutorService executor = Executors.newFixedThreadPool(amountOfThreads);

            for (Config.ConfigEntity entity : config.configEntities()) {
                System.out.println("Starting proxy on port " + entity.getLocalPort() + "...");
                InetSocketAddress local = new InetSocketAddress(entity.getLocalPort());
                InetSocketAddress remote = new InetSocketAddress(entity.getRemoteHost(), entity.getRemotePort());
                Proxy proxy = new Proxy(local, remote);
                executor.submit(proxy);

            }
        }

    }
}
