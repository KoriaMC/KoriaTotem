package fr.koriamc.totem.api.event;

import fr.koriamc.totem.model.TotemDefinition;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Appelé à la fin d'un totem, qu'il ait été détruit, arrêté par un administrateur
 * ou terminé par sa durée maximale (non annulable). Les blocs sont déjà retirés.
 */
public final class TotemEndEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final TotemDefinition totem;
    private final boolean destroyed;

    public TotemEndEvent(TotemDefinition totem, boolean destroyed) {
        this.totem = totem;
        this.destroyed = destroyed;
    }

    public TotemDefinition getTotem() {
        return totem;
    }

    /** true si le totem a été détruit, false s'il a été arrêté ou a expiré. */
    public boolean isDestroyed() {
        return destroyed;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
