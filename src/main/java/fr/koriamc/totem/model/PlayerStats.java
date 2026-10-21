package fr.koriamc.totem.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Statistiques d'un joueur (sérialisées en JSON) : blocs de totem cassés,
 * derniers blocs et récompenses en attente s'il était hors ligne.
 */
public final class PlayerStats {

    private String uuid;
    private String name;
    private long blocks;
    private int finalBlows;
    private int participations;
    private List<String> pending;

    public PlayerStats(UUID uuid, String name) {
        this.uuid = uuid.toString();
        this.name = name == null ? "?" : name;
        this.pending = new ArrayList<String>();
    }

    /** Recrée les collections absentes après une lecture JSON. */
    public void ensureInit() {
        if (pending == null) {
            pending = new ArrayList<String>();
        }
        if (name == null) {
            name = "?";
        }
    }

    /** Clé de stockage (UUID sous forme de texte). */
    public String getId() {
        return uuid;
    }

    public UUID getUniqueId() {
        return UUID.fromString(uuid);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name != null && !name.isEmpty()) {
            this.name = name;
        }
    }

    /** Blocs de totem cassés au total. */
    public long getBlocks() {
        return blocks;
    }

    public void addBlocks(long count) {
        blocks += Math.max(0, count);
    }

    /** Nombre de fois où ce joueur a cassé le dernier bloc d'un totem. */
    public int getFinalBlows() {
        return finalBlows;
    }

    public void addFinalBlow() {
        finalBlows++;
    }

    /** Nombre d'évènements auxquels le joueur a participé. */
    public int getParticipations() {
        return participations;
    }

    public void addParticipation() {
        participations++;
    }

    /** Récompenses à donner à la prochaine connexion. */
    public List<String> getPending() {
        return pending;
    }

    /** Un joueur sans statistique ni récompense en attente n'est pas sauvegardé. */
    public boolean isEmpty() {
        return blocks == 0 && finalBlows == 0 && participations == 0 && pending.isEmpty();
    }
}
