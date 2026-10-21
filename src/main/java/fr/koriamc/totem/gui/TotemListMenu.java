package fr.koriamc.totem.gui;

import fr.koriamc.totem.KoriaTotem;
import fr.koriamc.totem.command.SubCommand;
import fr.koriamc.totem.game.TotemManager;
import fr.koriamc.totem.model.MetaState;
import fr.koriamc.totem.model.TotemDefinition;
import fr.koriamc.totem.model.TotemSession;
import fr.koriamc.totem.util.Items;
import fr.koriamc.totem.util.Text;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /totem menu : liste des totems avec leur état, leur planning et leur dernier
 * vainqueur. L'apparence de chaque état et l'effet des clics viennent de
 * menus.json (liste). Actions propres : TELEPORTER, DEMARRER, ARRETER, TOP.
 */
final class TotemListMenu extends Menu {

    TotemListMenu(KoriaTotem plugin, Player player) {
        super(plugin, player);
    }

    @Override
    protected ConfigurationSection section() {
        return plugin.menus().section("liste");
    }

    @Override
    protected void render() {
        ConfigurationSection s = section();
        List<TotemDefinition> totems = new ArrayList<TotemDefinition>(plugin.catalog().all());
        List<Integer> slots = s.getIntegerList("cases");
        int perPage = Math.max(1, slots.size());
        pages = Math.max(1, (totems.size() + perPage - 1) / perPage);
        page = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < slots.size() && page * perPage + i < totems.size(); i++) {
            item(slots.get(i), totems.get(page * perPage + i));
        }
        Object[] vars = vars();
        ConfigurationSection items = s.getConfigurationSection("objets");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                setConfigured(items.getConfigurationSection(key), vars);
            }
        }
        if (page > 0) {
            setConfigured(s.getConfigurationSection("precedent"), vars);
        }
        if (page < pages - 1) {
            setConfigured(s.getConfigurationSection("suivant"), vars);
        }
    }

    /** Un totem : modèle en-cours, planifie, inactif ou invalide selon son état. */
    private void item(int slot, final TotemDefinition def) {
        TotemSession session = plugin.totems().get(def.getId());
        String state = session != null ? "en-cours"
                : !def.isPlayable() ? "invalide"
                : def.getSchedule().isEnabled() ? "planifie" : "inactif";
        ConfigurationSection template = section().getConfigurationSection("totems." + state);
        if (template == null) {
            return;
        }
        MetaState.Winner last = plugin.data().meta().getLast(def.getId());
        String nobody = plugin.msg().text("values.nobody");
        Object[] kv = {
                "totem", Text.color(def.getName()),
                "id", def.getId(),
                "icone", def.getIcon(),
                "monde", def.getWorldName().isEmpty() ? "?" : def.getWorldName(),
                "x", def.getX(),
                "y", def.getY(),
                "z", def.getZ(),
                "hauteur", def.getHeight(),
                "materiau", def.getMaterial(),
                "mode", plugin.msg().text("values.mode-" + def.getWinMode().name().toLowerCase(Locale.ROOT)),
                "reconstruction", plugin.msg().yesNo(def.isRebuild()),
                "duree", def.getMaxDurationMinutes() == 0
                        ? plugin.msg().text("values.unlimited") : def.getMaxDurationMinutes() + " min",
                "planning", plugin.display().schedule(def.getSchedule()),
                "prochain", plugin.msg().duration(def.getSchedule().millisUntilNext(ZonedDateTime.now(plugin.settings().zone))),
                "restants", session == null ? def.getHeight() : session.getRemaining(),
                "total", session == null ? def.getHeight() : session.getTotal(),
                "barre", plugin.display().bar(session == null ? 0 : session.getProgress()),
                "pourcentage", plugin.display().percent(session == null ? 0 : session.getProgress()),
                "faction", session == null || session.getCurrentName() == null ? nobody : session.getCurrentName(),
                "etat", session == null ? plugin.msg().text("display.state-waiting") : plugin.display().state(session),
                "meilleur", session == null || session.getBestPlayer() == null
                        ? nobody : session.getName(session.getBestPlayer()),
                "dernier", last == null ? plugin.msg().text("values.none") : last.getTag(),
                "dernier_joueur", last == null ? plugin.msg().text("values.none") : last.getPlayer(),
                "dernier_temps", last == null ? plugin.msg().text("values.none") : plugin.msg().since(last.getTime()),
                "points", def.getVictoryPoints(),
                "recompenses", plugin.rewards().describe(def.getWinnerRewards()),
                "label", plugin.commands().getLabel()
        };
        set(slot, Items.fromSection(template, kv), clickHandler(template, new CustomAction() {
            @Override
            public boolean run(String type, String value) {
                return action(type, def);
            }
        }, kv));
    }

    /** Actions propres aux totems de la liste. */
    private boolean action(String type, TotemDefinition def) {
        if (type.equals("TELEPORTER")) {
            teleport(def);
            return true;
        }
        if (type.equals("DEMARRER")) {
            if (!isAdmin()) {
                return true;
            }
            TotemManager.Result result = plugin.totems().start(def.getId(), false);
            plugin.msg().send(player, "admin.start-" + result.name().toLowerCase(Locale.ROOT),
                    "totem", Text.color(def.getName()), "id", def.getId());
            refreshLater();
            return true;
        }
        if (type.equals("ARRETER")) {
            if (!isAdmin()) {
                return true;
            }
            if (plugin.totems().stopTotem(def.getId())) {
                plugin.msg().send(player, "admin.stopped", "totem", Text.color(def.getName()));
            } else {
                plugin.msg().send(player, "admin.not-running", "totem", Text.color(def.getName()));
            }
            refreshLater();
            return true;
        }
        if (type.equals("TOP")) {
            plugin.menus().openTop(player, def.getId());
            return true;
        }
        return false;
    }

    /** Téléportation à côté du totem (permission de la commande tp). */
    private void teleport(TotemDefinition def) {
        SubCommand command = plugin.commands().get("tp");
        if (command != null && !command.hasPermission(player)) {
            plugin.msg().send(player, "general.no-permission");
            return;
        }
        Location spawn = def.isPositioned() ? def.getSpawn() : null;
        if (spawn == null) {
            plugin.msg().send(player, "totem.no-position", "totem", Text.color(def.getName()), "id", def.getId());
            return;
        }
        player.closeInventory();
        player.teleport(spawn);
        plugin.msg().send(player, "totem.teleported", "totem", Text.color(def.getName()));
    }

    private boolean isAdmin() {
        SubCommand command = plugin.commands().get("start");
        if (command == null || command.hasPermission(player)) {
            return true;
        }
        plugin.msg().send(player, "general.no-permission");
        return false;
    }
}
