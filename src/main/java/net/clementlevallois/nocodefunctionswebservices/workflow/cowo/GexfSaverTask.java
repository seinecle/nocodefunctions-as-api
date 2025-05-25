package net.clementlevallois.nocodefunctionswebservices.workflow.cowo;

import net.clementlevallois.nocodefunctionswebservices.workflow.topics.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles saving the GEXF content to a file.
 */
public class GexfSaverTask {

    private static final Logger LOGGER = Logger.getLogger(GexfSaverTask.class.getName());

    private final String gexfContent;
    private final Path targetDirectory;
    private final String baseFilename; // Usually dataPersistenceId

    /**
     * Constructor for the GEXF saving task.
     *
     * @param gexfContent     The GEXF XML content as a string.
     * @param targetDirectory The directory where the file should be saved.
     * @param dataPersistenceUniqueId    The base name for the file (e.g., dataPersistenceId), "_result.gexf" will be appended.
     */
    public GexfSaverTask(String gexfContent, Path targetDirectory, String dataPersistenceUniqueId) {
        this.gexfContent = Objects.requireNonNull(gexfContent, "GEXF content cannot be null");
        this.targetDirectory = Objects.requireNonNull(targetDirectory, "Target directory cannot be null");
        this.baseFilename = Objects.requireNonNull(dataPersistenceUniqueId, "Base filename cannot be null");
    }

    /**
     * Saves the GEXF content to the specified file.
     *
     * @return The absolute path of the saved GEXF file as a String.
     * @throws IOException If an error occurs during file writing.
     */
    public String save() throws IOException {
        if (gexfContent.isBlank()) {
            LOGGER.log(Level.WARNING, "GEXF content is blank, cannot save file for {0}", baseFilename);
            throw new IOException("Cannot save blank GEXF content.");
        }

        Path gexfPath = targetDirectory.resolve(baseFilename + "_result.gexf");
        LOGGER.log(Level.INFO, "Attempting to save GEXF to: {0}", gexfPath.toAbsolutePath());

        try {
            Files.writeString(gexfPath, gexfContent, StandardCharsets.UTF_8);
            LOGGER.log(Level.INFO, "Successfully saved GEXF to: {0}", gexfPath.toAbsolutePath());
            return gexfPath.toAbsolutePath().toString();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to write GEXF file: " + gexfPath.toAbsolutePath(), e);
            // Attempt to delete partially written file? Maybe not necessary.
            throw e; // Re-throw the exception to be handled by the caller
        }
    }
}