package com.example.treedms.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage")
public class FileStorageProperties {

    private String root = "./storage/files";

    public String getRoot() {
        return root;
    }

    public void setRoot(String root) {
        this.root = root;
    }

    public Path rootPath() {
        return Paths.get(root).toAbsolutePath().normalize();
    }
}
