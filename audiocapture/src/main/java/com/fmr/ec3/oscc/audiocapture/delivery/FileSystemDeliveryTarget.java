package com.fmr.ec3.oscc.audiocapture.delivery;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Writes WAV data to a local or NFS filesystem using atomic move.
 */
public class FileSystemDeliveryTarget implements AudioDeliveryTarget {

    private final Path directory;
    private final String urlPrefix;

    public FileSystemDeliveryTarget(Path directory, String urlPrefix) {
        this.directory = directory;
        this.urlPrefix = urlPrefix;
    }

    @Override
    public String deliver(byte[] wavData, String sessionId, String stepId) throws IOException {
        String fileName = stepId + ".wav";
        Path target = directory.resolve(fileName);
        Path temp = directory.resolve(fileName + ".tmp");

        try (OutputStream out = Files.newOutputStream(temp)) {
            out.write(wavData);
        }
        Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

        return urlPrefix + "/" + fileName;
    }
}
