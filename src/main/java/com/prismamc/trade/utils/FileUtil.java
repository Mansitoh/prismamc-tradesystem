package com.prismamc.trade.utils;

import com.prismamc.trade.Plugin;
import org.bukkit.configuration.file.YamlConfiguration;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.nio.channels.FileChannel;

/**
 * Utility class for file operations and YAML configuration handling.
 * This class provides functionality for managing configuration files
 * and file operations in the plugin.
 *
 * @author Mansitoh
 * @version 2.0
 */
public class FileUtil {
    private final String fileName;
    private File file;
    private final Plugin plugin;
    private YamlConfiguration config;
    private long lastModified;

    /**
     * Constructs a new FileUtil instance.
     *
     * @param plugin   The plugin instance
     * @param fileName The name of the file (with extension)
     */
    public FileUtil(@Nonnull Plugin plugin, @Nonnull String fileName) {
        this.plugin = plugin;
        this.fileName = fileName;
        this.file = new File(plugin.getDataFolder(), fileName);
        this.config = new YamlConfiguration();
        initialize();
    }

    /**
     * Gets the name of the file.
     *
     * @return The file name
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * Gets the file object.
     *
     * @return The file object
     */
    public File getFile() {
        return file;
    }

    /**
     * Gets the plugin instance.
     *
     * @return The plugin instance
     */
    public Plugin getPlugin() {
        return plugin;
    }

    /**
     * Gets the configuration object.
     *
     * @return The YamlConfiguration object
     */
    public YamlConfiguration getConfig() {
        // Verificar si el archivo ha sido modificado antes de devolver la configuración
        if (file.exists() && file.lastModified() > lastModified) {
            loadConfig();
        }
        return config;
    }

    /**
     * Initializes the file and configuration.
     */
    private void initialize() {
        try {
            if (!fileName.endsWith(".yml")) {
                Files.createDirectories(file.toPath());
                return;
            }

            if (!file.exists()) {
                file.getParentFile().mkdirs();

                if (plugin.getResource(fileName) == null) {
                    Files.createFile(file.toPath());
                } else {
                    plugin.saveResource(fileName, false);
                }
            }

            loadConfig();
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create/access file: " + fileName, e);
        }
    }

    private synchronized void loadConfig() {
        try {
            if (file.exists()) {
                config = YamlConfiguration.loadConfiguration(file);
                lastModified = file.lastModified();
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error loading config: " + fileName, e);
        }
    }

    /**
     * Reloads the configuration from the file.
     */
    public void reload() {
        CompletableFuture.runAsync(() -> {
            try {
                file = new File(plugin.getDataFolder(), fileName);

                if (!file.exists() && plugin.getResource(fileName) != null) {
                    plugin.saveResource(fileName, false);
                }

                // Verificar si el archivo ha sido modificado
                if (file.exists() && file.lastModified() > lastModified) {
                    loadConfig();
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to reload file: " + fileName, e);
            }
        });
    }

    /**
     * Saves the configuration to the file.
     *
     * @return true if save was successful, false otherwise
     */
    public boolean save() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!file.exists()) {
                    Files.createFile(file.toPath());
                }

                // Usar un archivo temporal para escritura segura
                Path tempFile = Files.createTempFile(file.toPath().getParent(), "temp", ".yml");
                try {
                    config.save(tempFile.toFile());
                    // Mover el archivo temporal al archivo real de forma atómica
                    Files.move(tempFile, file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                    lastModified = file.lastModified();
                    return true;
                } finally {
                    Files.deleteIfExists(tempFile);
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save file: " + fileName, e);
                return false;
            }
        }).join();
    }

    /**
     * Creates a backup of the file with a timestamp.
     *
     * @return true if the backup was successful, false otherwise
     */
    public boolean backup() {
        if (!file.exists()) {
            return false;
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                String backupName = fileName.replace(".yml", "") +
                        "_backup_" +
                        System.currentTimeMillis() +
                        ".yml";
                Path backupDir = plugin.getDataFolder().toPath().resolve("backups");
                Files.createDirectories(backupDir);

                Path backupFile = backupDir.resolve(backupName);

                // Usar NIO para una copia más eficiente
                try (FileChannel source = FileChannel.open(file.toPath(), StandardOpenOption.READ);
                        FileChannel target = FileChannel.open(backupFile,
                                StandardOpenOption.CREATE,
                                StandardOpenOption.WRITE)) {
                    source.transferTo(0, source.size(), target);
                }

                return true;
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to create backup of " + fileName, e);
                return false;
            }
        }).join();
    }

    /**
     * Copies a directory or file to another location.
     *
     * @param source      The source file or directory
     * @param destination The destination file or directory
     * @param excludes    Optional array of file/directory names to exclude
     * @throws IOException if an I/O error occurs
     */
    public static void copy(@Nonnull File source, @Nonnull File destination, @Nullable String... excludes)
            throws IOException {
        Path sourcePath = source.toPath();
        Path destinationPath = destination.toPath();

        Files.walkFileTree(sourcePath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (shouldExclude(dir.getFileName().toString(), excludes)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }

                Path targetDir = destinationPath.resolve(sourcePath.relativize(dir));
                try {
                    Files.copy(dir, targetDir, StandardCopyOption.COPY_ATTRIBUTES);
                } catch (FileAlreadyExistsException e) {
                    if (!Files.isDirectory(targetDir)) {
                        throw e;
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (shouldExclude(file.getFileName().toString(), excludes)) {
                    return FileVisitResult.CONTINUE;
                }

                Path targetFile = destinationPath.resolve(sourcePath.relativize(file));
                Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Recursively deletes a directory or file.
     *
     * @param path The path to delete
     * @throws IOException if an I/O error occurs
     */
    public static void delete(@Nonnull File path) throws IOException {
        Files.walkFileTree(path.toPath(), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc != null)
                    throw exc;
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Safely deletes a directory or file, suppressing any exceptions.
     *
     * @param path The path to delete
     * @return true if the deletion was successful, false otherwise
     */
    public static boolean safeDelete(@Nonnull File path) {
        try {
            delete(path);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Checks if a file or directory name should be excluded from operations.
     *
     * @param name     The name to check
     * @param excludes Array of names to exclude
     * @return true if the name should be excluded, false otherwise
     */
    private static boolean shouldExclude(String name, String... excludes) {
        if (excludes == null || excludes.length == 0) {
            return false;
        }

        for (String exclude : excludes) {
            if (exclude != null && name.equalsIgnoreCase(exclude)) {
                return true;
            }
        }
        return false;
    }
}