package fr.koriamc.totem.api.event;

import fr.koriamc.totem.model.TotemDefinition;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * Appelé après la destruction d'un totem, une fois les récompenses données (non annulable).
 */
public final class TotemDestroyEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final TotemDefinition totem;
    private final String factionId;
    private final String name;
    private final UUID lastBreaker;
    private final long points;

    public TotemDestroyEvent(TotemDefinition totem, String factionId, String name, UUID lastBreaker, long points) {
        this.totem = totem;
        this.factionId = factionId;
        this.name = name;
        this.lastBreaker = lastBreaker;
        this.points = points;
    }

    public TotemDefinition getTotem() {
        return totem;
    }

    /** Faction gagnante, ou null si un joueur sans faction a gagné. */
    public String getFactionId() {
        return factionId;
    }

    /** Tag de la faction gagnante, ou pseudo du joueur seul. */
    public String getName() {
        return name;
    }

    /** Joueur qui a cassé le dernier bloc. */
    public UUID getLastBreaker() {
        return lastBreaker;
    }

    /** Points de faction crédités. */
    public long getPoints() {
        return points;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
