package fr.koriamc.totem.listener;

import fr.koriamc.totem.KoriaTotem;
import fr.koriamc.totem.game.TotemManager;
import fr.koriamc.totem.model.PlayerStats;
import fr.koriamc.totem.model.TotemSession;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Casse des blocs du totem, protection du totem et de ses abords, remise des
 * récompenses gardées pour les joueurs qui étaient hors ligne.
 */
public final class TotemListener implements Listener {

    /** Délai minimal entre deux messages de protection pour un même joueur. */
    private static final long MESSAGE_DELAY_MS = 3000L;

    private final KoriaTotem plugin;
    private final Map<UUID, Long> lastMessage = new HashMap<UUID, Long>();

    public TotemListener(KoriaTotem plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerStats stats = plugin.data().findPlayer(player.getUniqueId());
        if (stats == null) {
            return;
        }
        stats.setName(player.getName());
        plugin.rewards().deliverPending(player, stats);
    }

    /**
     * Les blocs du totem se cassent même là où les autres plugins l'interdisent
     * (zone de guerre, zone protégée) : le totem est fait pour être attaqué.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        TotemSession session = plugin.totems().sessionAt(block.getLocation());
        if (session != null) {
            event.setCancelled(true);
            TotemManager.BreakResult result = plugin.totems().breakBlock(session, player, block);
            if (result == TotemManager.BreakResult.REFUSE) {
                warn(player, "totem.cannot-break");
            }
            return;
        }
        if (!event.isCancelled() && plugin.settings().protectBreak && isProtected(player, block)) {
            event.setCancelled(true);
            warn(player, "protection.protected-area");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (plugin.settings().protectPlace && isProtected(event.getPlayer(), event.getBlock())) {
            event.setCancelled(true);
            warn(event.getPlayer(), "protection.protected-area");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        filter(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        filter(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            if (plugin.totems().sessionAt(block.getLocation()) != null) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            if (plugin.totems().sessionAt(block.getLocation()) != null) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /** Les explosions n'abîment jamais le totem, ni ses abords si protection.explosions est actif. */
    private void filter(List<Block> blocks) {
        Iterator<Block> it = blocks.iterator();
        while (it.hasNext()) {
            Block block = it.next();
            if (plugin.totems().sessionAt(block.getLocation()) != null
                    || (plugin.settings().protectExplosions && plugin.totems().protectedAt(block.getLocation()) != null)) {
                it.remove();
            }
        }
    }

    /** Le bloc est-il dans le rayon de protection d'un totem en cours ? */
    private boolean isProtected(Player player, Block block) {
        return !player.hasPermission("koriatotem.bypass") && plugin.totems().protectedAt(block.getLocation()) != null;
    }

    /** Message au joueur, au maximum une fois toutes les trois secondes. */
    private void warn(Player player, String key) {
        Long last = lastMessage.get(player.getUniqueId());
        long now = System.currentTimeMillis();
        if (last != null && now - last < MESSAGE_DELAY_MS) {
            return;
        }
        lastMessage.put(player.getUniqueId(), now);
        plugin.msg().send(player, key);
    }
}
