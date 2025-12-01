package com.scapes.impl;

import com.scapes.core.SystemHandler;
import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.win32.W32APIOptions;

public class WindowsSystemHandler implements SystemHandler {
    private static final Logger logger = LoggerFactory.getLogger(WindowsSystemHandler.class);
    private static final Path folderPath = Path.of(System.getProperty("user.home"), "Pictures", "Scapes");

    public interface User32 extends Library {
        User32 INSTANCE = Native.load("user32", User32.class, W32APIOptions.UNICODE_OPTIONS);

        // spi codes
        int SPI_SETDESKWALLPAPER = 0x0014;
        int SPIF_UPDATEINIFILE = 0x01;
        int SPIF_SENDWININICHANGE = 0x02;

        boolean SystemParametersInfo(int uiAction, int uiParam, String pvParam, int fWinIni);
    }

    @Override
    public File downloadImage(String url, String filename) {
        try {
            logger.info("Downloading image from: " + url);
            
            if (!Files.exists(folderPath)) Files.createDirectories(folderPath);
            String safeFilename = filename.replaceAll("[^a-zA-Z0-9.-]", "_");
            Path destination = folderPath.resolve(safeFilename);

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(new URI(url))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .GET()
                    .build();
            
            // download image as stream
            InputStream stream = client.send(req, HttpResponse.BodyHandlers.ofInputStream()).body();
            // save to file
            Files.copy(stream, destination, StandardCopyOption.REPLACE_EXISTING);
            stream.close();
            // give some time for file system to register the new file
            Thread.sleep(100); 

            return destination.toFile();
        } catch (Exception e) {
            logger.error("Failed to download image from: " + url, e);
            return null; // null if download fails
        }
    }

    // set wallpaper using Windows API
    @Override
    public boolean setWallpaper(File imageFile) {
        logger.info("Setting wallpaper to file: " + (imageFile != null ? imageFile.getAbsolutePath() : "null"));
        if (imageFile == null || !imageFile.exists()) return false;

        String absolutePath = imageFile.getAbsolutePath().replace("/", "\\");
        boolean result = User32.INSTANCE.SystemParametersInfo(
            User32.SPI_SETDESKWALLPAPER,
            0,
            absolutePath,
            User32.SPIF_UPDATEINIFILE | User32.SPIF_SENDWININICHANGE
        );

        if (!result) {
            logger.error("Failed to set wallpaper using SystemParametersInfo.");
        } else {
            logger.info("Wallpaper set successfully.");
        }

        return result;
    }
}