package fr.koriamc.totem.gui;

import fr.koriamc.totem.KoriaTotem;
import fr.koriamc.totem.model.FactionStats;
import fr.koriamc.totem.model.TotemDefinition;
import fr.koriamc.totem.storage.DataManager;
import fr.koriamc.totem.util.Items;
import fr.koriamc.totem.util.Text;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * /totem top : classement des factions, au total ou pour un totem précis.
 * Apparence dans menus.json (top).
 */
final class TopMenu extends Menu {

    private final String totemId;

    TopMenu(KoriaTotem plugin, Player player, String totemId) {
        super(plugin, player);
        this.totemId = totemId;
    }

    @Override
    protected ConfigurationSection section() {
        return plugin.menus().section("top");
    }

    @Override
    protected Object[] vars() {
        TotemDefinition def = totemId == null ? null : plugin.catalog().get(totemId);
        Object[] base = super.vars();
        Object[] all = new Object[base.length + 4];
        System.arraycopy(base, 0, all, 0, base.length);
        all[base.length] = "titre";
        all[base.length + 1] = def == null ? plugin.msg().text("top.all") : Text.color(def.getName());
        all[base.length + 2] = "id";
        all[base.length + 3] = def == null ? "" : def.getId();
        return all;
    }

    @Override
    protected void render() {
        ConfigurationSection s = section();
        List<Integer> slots = s.getIntegerList("cases");
        List<FactionStats> top = plugin.data().top(totemId, Math.max(slots.size(), plugin.settings().topShown));
        ConfigurationSection line = s.getConfigurationSection("ligne");
        ConfigurationSection empty = s.getConfigurationSection("vide");
        String factionId = plugin.factionHook().getFactionId(player);
        TotemDefinition def = totemId == null ? null : plugin.catalog().get(totemId);
        String title = def == null ? plugin.msg().text("top.all") : Text.color(def.getName());
        for (int i = 0; i < slots.size(); i++) {
            if (i < top.size()) {
                FactionStats stats = top.get(i);
                boolean own = factionId != null && factionId.equals(stats.getFactionId());
                Object[] kv = {
                        "rang", i + 1,
                        "faction", stats.getTag(),
                        "victoires", DataManager.wins(stats, totemId),
                        "victoires_total", stats.getWins(),
                        "blocs", stats.getBlocks(),
                        "derniere", stats.getLastWin() == 0
                                ? plugin.msg().text("values.never") : plugin.msg().since(stats.getLastWin()),
                        "titre", title,
                        "label", plugin.commands().getLabel()
                };
                set(slots.get(i), Items.fromSection(line, "materiau", own ? Boolean.TRUE : null, kv),
                        clickHandler(line, null, kv));
            } else if (empty != null) {
                set(slots.get(i), Items.fromSection(empty, vars()), null);
            }
        }
        Object[] vars = vars();
        ConfigurationSection items = s.getConfigurationSection("objets");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                setConfigured(items.getConfigurationSection(key), vars);
            }
        }
    }
}
