package fr.koriamc.totem.api.event;

import fr.koriamc.totem.model.TotemDefinition;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Appelé quand un joueur casse un bloc du totem, avant qu'il soit compté.
 * L'annuler laisse le bloc en place.
 */
public final class TotemBreakEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final TotemDefinition totem;
    private final Player player;
    private final String factionId;
    private final Block block;
    private boolean cancelled;

    public TotemBreakEvent(TotemDefinition totem, Player player, String factionId, Block block) {
        this.totem = totem;
        this.player = player;
        this.factionId = factionId;
        this.block = block;
    }

    public TotemDefinition getTotem() {
        return totem;
    }

    public Player getPlayer() {
        return player;
    }

    /** Faction du joueur, ou null s'il n'en a pas. */
    public String getFactionId() {
        return factionId;
    }

    public Block getBlock() {
        return block;
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
