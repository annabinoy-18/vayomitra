package com.vayomitra;

import com.vayomitra.config.AppConfig;
import com.vayomitra.db.Database;
import com.vayomitra.db.SchemaMigrator;
import com.vayomitra.http.ApiServer;
import com.vayomitra.service.SeedService;

import java.nio.file.Path;

public final class App {
    public static void main(String[] args) throws Exception {
        Path projectRoot = findProjectRoot();
        AppConfig config = AppConfig.load(projectRoot);
        Database database = new Database(config);
        new SchemaMigrator(database, projectRoot.resolve("database").resolve("migrations")).migrate();
        new SeedService(database, config).seedIfEmpty();
        new ApiServer(config, database).start();
        System.out.println("Vayomitra API listening on http://localhost:" + config.serverPort());
        System.out.println("Frontend origin allowed: " + config.frontendOrigin());
    }

    static Path findProjectRoot() {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if (cwd.getFileName() != null && cwd.getFileName().toString().equals("backend")) {
            return cwd.getParent();
        }
        if (cwd.resolve("backend").toFile().isDirectory() && cwd.resolve("database").toFile().isDirectory()) {
            return cwd;
        }
        Path parent = cwd.getParent();
        if (parent != null && parent.resolve("database").toFile().isDirectory()) {
            return parent;
        }
        return cwd;
    }
}
