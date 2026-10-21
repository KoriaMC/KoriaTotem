package fr.koriamc.totem.storage;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Stockage dans trois fichiers JSON (joueurs, factions, cycle) avec copies de
 * sécurité tournantes et écriture atomique.
 */
public final class JsonStorage implements Storage {

    private final File folder;
    private final int backups;
    private final Logger logger;
    private final String[] lastWritten = new String[3];

    public JsonStorage(File folder, int backups, Logger logger) {
        this.folder = folder;
        this.backups = Math.max(0, backups);
        this.logger = logger;
    }

    @Override
    public String getName() {
        return "JSON";
    }

    @Override
    public void init() throws IOException {
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IOException("Impossible de créer " + folder);
        }
    }

    @Override
    public DataSnapshot load() throws IOException {
        DataSnapshot data = new DataSnapshot();
        read(new File(folder, "players.json"), data.players);
        read(new File(folder, "factions.json"), data.factions);
        read(new File(folder, "meta.json"), data.meta);
        return data;
    }

    private void read(File file, Map<String, String> target) throws IOException {
        if (!file.exists()) {
            return;
        }
        Reader reader = new InputStreamReader(Files.newInputStream(file.toPath()), StandardCharsets.UTF_8);
        try {
            JsonElement root = new JsonParser().parse(reader);
            if (root == null || !root.isJsonObject()) {
                return;
            }
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject().entrySet()) {
                target.put(entry.getKey(), entry.getValue().toString());
            }
        } catch (RuntimeException e) {
            throw new IOException("Fichier JSON corrompu : " + file.getName() + " (" + e.getMessage() + ")", e);
        } finally {
            reader.close();
        }
    }

    @Override
    public void save(DataSnapshot snapshot) throws IOException {
        write(0, new File(folder, "players.json"), snapshot.players);
        write(1, new File(folder, "factions.json"), snapshot.factions);
        write(2, new File(folder, "meta.json"), snapshot.meta);
    }

    private void write(int index, File file, Map<String, String> entries) throws IOException {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            sb.append(first ? "\n" : ",\n");
            first = false;
            sb.append("  ").append(new JsonPrimitive(entry.getKey()).toString()).append(": ").append(entry.getValue());
        }
        sb.append(first ? "}" : "\n}").append('\n');
        String content = sb.toString();
        if (content.equals(lastWritten[index])) {
            return;
        }
        rotateBackups(file);
        File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
        Files.write(tmp.toPath(), content.getBytes(StandardCharsets.UTF_8));
        try {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        lastWritten[index] = content;
    }

    private void rotateBackups(File file) {
        if (backups <= 0 || !file.exists()) {
            return;
        }
        File dir = new File(folder, "backups");
        if (!dir.exists() && !dir.mkdirs()) {
            logger.warning("Impossible de créer le dossier de sauvegarde " + dir);
            return;
        }
        try {
            for (int i = backups - 1; i >= 1; i--) {
                File from = new File(dir, file.getName() + "." + i);
                if (from.exists()) {
                    Files.move(from.toPath(), new File(dir, file.getName() + "." + (i + 1)).toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            }
            Files.copy(file.toPath(), new File(dir, file.getName() + ".1").toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            logger.warning("Copie de sécurité impossible pour " + file.getName() + " : " + e.getMessage());
        }
    }

    @Override
    public void close() {
        // rien à fermer
    }
}
