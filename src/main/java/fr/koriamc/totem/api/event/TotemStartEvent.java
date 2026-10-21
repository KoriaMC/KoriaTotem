package fr.koriamc.totem.api.event;

import fr.koriamc.totem.model.TotemDefinition;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Appelé juste avant l'apparition d'un totem (démarrage manuel ou planifié).
 * L'annuler empêche le démarrage.
 */
public final class TotemStartEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final TotemDefinition totem;
    private boolean cancelled;

    public TotemStartEvent(TotemDefinition totem) {
        this.totem = totem;
    }

    public TotemDefinition getTotem() {
        return totem;
    }

    public String getTotemId() {
        return totem.getId();
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
