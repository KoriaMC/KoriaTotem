package fr.koriamc.totem.command.impl;

import fr.koriamc.totem.KoriaTotem;
import fr.koriamc.totem.command.CommandContext;
import fr.koriamc.totem.command.SubCommand;
import fr.koriamc.totem.config.JsonFile;
import fr.koriamc.totem.game.TotemManager;
import fr.koriamc.totem.model.TotemDefinition;
import fr.koriamc.totem.model.TotemSession;
import fr.koriamc.totem.util.Items;
import fr.koriamc.totem.util.Text;
import org.bukkit.Location;
import org.bukkit.block.Block;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Commandes d'administration : téléportation, démarrage, arrêt, création et
 * suppression de totems, éditeur, sauvegarde et rechargement.
 */
public final class AdminCommands {

    /** Suppression en attente de confirmation : joueur -> identifiant et instant. */
    private static final Map<UUID, String> CONFIRM_TARGET = new HashMap<UUID, String>();
    private static final Map<UUID, Long> CONFIRM_TIME = new HashMap<UUID, Long>();
    private static final long CONFIRM_MS = 10000L;

    private AdminCommands() {
    }

    /** Complète avec les identifiants des totems. */
    private static List<String> completeIds(KoriaTotem plugin, CommandContext ctx) {
        return ctx.size() == 1 ? Text.complete(ctx.arg(0), plugin.catalog().ids()) : Collections.<String>emptyList();
    }

    /** Position visée : le bloc regardé (à 6 blocs), sinon le bloc sous les pieds du joueur. */
    @SuppressWarnings("deprecation")
    private static Location target(CommandContext ctx) {
        Block looked = ctx.player.getTargetBlock((java.util.HashSet<Byte>) null, 6);
        if (looked != null && looked.getType() != org.bukkit.Material.AIR) {
            return looked.getLocation().add(0, 1, 0);
        }
        return ctx.player.getLocation().getBlock().getLocation();
    }

    /** /totem tp &lt;totem&gt; */
    public static final class Teleport extends SubCommand {
        public Teleport(KoriaTotem plugin) {
            super(plugin, "tp", true, 1, true);
        }

        @Override
        public void execute(CommandContext ctx) {
            TotemDefinition def = plugin.catalog().get(ctx.arg(0));
            if (def == null) {
                ctx.msg("totem.unknown", "totem", ctx.arg(0));
                return;
            }
            Location spawn = def.isPositioned() ? def.getSpawn() : null;
            if (spawn == null) {
                ctx.msg("totem.no-position", "totem", Text.color(def.getName()), "id", def.getId());
                return;
            }
            ctx.player.teleport(spawn);
            ctx.msg("totem.teleported", "totem", Text.color(def.getName()));
        }

        @Override
        public List<String> complete(CommandContext ctx) {
            return completeIds(plugin, ctx);
        }
    }

    /** /totem start &lt;totem&gt; [force] */
    public static final class Start extends SubCommand {
        public Start(KoriaTotem plugin) {
            super(plugin, "start", false, 1, true);
        }

        @Override
        public void execute(CommandContext ctx) {
            boolean force = ctx.size() > 1 && Text.parseBoolean(ctx.arg(1), true);
            TotemManager.Result result = plugin.totems().start(ctx.arg(0), force);
            TotemDefinition def = plugin.catalog().get(ctx.arg(0));
            ctx.msg("admin.start-" + result.name().toLowerCase(Locale.ROOT),
                    "totem", def == null ? ctx.arg(0) : Text.color(def.getName()), "id", ctx.arg(0));
        }

        @Override
        public List<String> complete(CommandContext ctx) {
            if (ctx.size() == 2) {
                return Text.complete(ctx.arg(1), Arrays.asList("force"));
            }
            return completeIds(plugin, ctx);
        }
    }

    /** /totem stop [totem] : sans argument, arrête tous les totems en cours */
    public static final class Stop extends SubCommand {
        public Stop(KoriaTotem plugin) {
            super(plugin, "stop", false, 0, true);
        }

        @Override
        public void execute(CommandContext ctx) {
            if (ctx.size() == 0) {
                if (!plugin.totems().isRunning()) {
                    ctx.msg("admin.nothing-running");
                    return;
                }
                plugin.totems().stopAll(true);
                ctx.msg("admin.stopped-all");
                return;
            }
            TotemDefinition def = plugin.catalog().get(ctx.arg(0));
            String name = def == null ? ctx.arg(0) : Text.color(def.getName());
            if (plugin.totems().stopTotem(ctx.arg(0))) {
                ctx.msg("admin.stopped", "totem", name);
            } else {
                ctx.msg("admin.not-running", "totem", name);
            }
        }

        @Override
        public List<String> complete(CommandContext ctx) {
            if (ctx.size() != 1) {
                return Collections.<String>emptyList();
            }
            List<String> running = new ArrayList<String>();
            for (TotemSession session : plugin.totems().sessions()) {
                running.add(session.getDefinition().getId());
            }
            return Text.complete(ctx.arg(0), running);
        }
    }

    /** /totem create &lt;nom&gt; : ajoute un totem dans totems.json, posé sur le bloc regardé */
    public static final class Create extends SubCommand {
        public Create(KoriaTotem plugin) {
            super(plugin, "create", true, 1, true);
        }

        @Override
        public void execute(CommandContext ctx) {
            String id = ctx.arg(0).toLowerCase(Locale.ROOT);
            if (!id.matches("[a-z0-9_-]+")) {
                ctx.msg("admin.invalid-id");
                return;
            }
            if (plugin.catalog().get(id) != null) {
                ctx.msg("admin.already-exists", "id", id);
                return;
            }
            Location at = target(ctx);
            Map<String, Object> position = new LinkedHashMap<String, Object>();
            position.put("x", at.getBlockX());
            position.put("y", at.getBlockY());
            position.put("z", at.getBlockZ());
            Map<String, Object> schedule = new LinkedHashMap<String, Object>();
            schedule.put("actif", Boolean.FALSE);
            schedule.put("jours", new ArrayList<Object>());
            schedule.put("heures", new ArrayList<Object>());
            Map<String, Object> rewards = new LinkedHashMap<String, Object>();
            rewards.put("cible", plugin.settings().target.name());
            rewards.put("vainqueur", new ArrayList<Object>());
            rewards.put("participants", new ArrayList<Object>());
            Map<String, Object> totem = new LinkedHashMap<String, Object>();
            totem.put("nom", "&cTotem " + Items.capitalize(id));
            totem.put("icone", plugin.settings().material);
            totem.put("monde", at.getWorld().getName());
            totem.put("position", position);
            totem.put("materiau", plugin.settings().material);
            totem.put("hauteur", plugin.settings().height);
            totem.put("mode-victoire", plugin.settings().winMode.name().replace('_', '-'));
            totem.put("reconstruction", plugin.settings().rebuild);
            totem.put("duree-max-minutes", plugin.settings().maxDurationMinutes);
            totem.put("planning", schedule);
            totem.put("points-victoire", plugin.settings().victoryPoints);
            totem.put("recompenses", rewards);
            plugin.totemsFile().set(Collections.singletonList(id), totem);
            if (!save(ctx)) {
                return;
            }
            ctx.msg("admin.created", "id", id, "monde", at.getWorld().getName(),
                    "x", at.getBlockX(), "y", at.getBlockY(), "z", at.getBlockZ());
        }
    }

    /** /totem delete &lt;totem&gt; */
    public static final class Delete extends SubCommand {
        public Delete(KoriaTotem plugin) {
            super(plugin, "delete", false, 1, true);
        }

        @Override
        public void execute(CommandContext ctx) {
            TotemDefinition def = plugin.catalog().get(ctx.arg(0));
            if (def == null) {
                ctx.msg("totem.unknown", "totem", ctx.arg(0));
                return;
            }
            UUID uuid = ctx.player == null ? new UUID(0, 0) : ctx.player.getUniqueId();
            Long time = CONFIRM_TIME.get(uuid);
            if (!def.getId().equals(CONFIRM_TARGET.get(uuid)) || time == null
                    || System.currentTimeMillis() - time > CONFIRM_MS) {
                CONFIRM_TARGET.put(uuid, def.getId());
                CONFIRM_TIME.put(uuid, System.currentTimeMillis());
                ctx.msg("admin.confirm-delete", "id", def.getId(), "totem", Text.color(def.getName()));
                return;
            }
            CONFIRM_TARGET.remove(uuid);
            CONFIRM_TIME.remove(uuid);
            plugin.totems().stopTotem(def.getId());
            plugin.totemsFile().remove(Collections.singletonList(def.getId()));
            if (!save(ctx)) {
                return;
            }
            ctx.msg("admin.deleted", "id", def.getId());
        }

        @Override
        public List<String> complete(CommandContext ctx) {
            return completeIds(plugin, ctx);
        }
    }

    /** /totem position &lt;totem&gt; : place le totem sur le bloc regardé (ou sous les pieds) */
    public static final class SetPosition extends SubCommand {
        public SetPosition(KoriaTotem plugin) {
            super(plugin, "position", true, 1, true);
        }

        @Override
        public void execute(CommandContext ctx) {
            TotemDefinition def = plugin.catalog().get(ctx.arg(0));
            if (def == null) {
                ctx.msg("totem.unknown", "totem", ctx.arg(0));
                return;
            }
            if (plugin.totems().get(def.getId()) != null) {
                ctx.msg("admin.running-cannot-move", "totem", Text.color(def.getName()));
                return;
            }
            Location at = target(ctx);
            JsonFile file = plugin.totemsFile();
            String id = def.getId();
            file.set(Arrays.asList(id, "monde"), at.getWorld().getName());
            file.set(Arrays.asList(id, "position", "x"), at.getBlockX());
            file.set(Arrays.asList(id, "position", "y"), at.getBlockY());
            file.set(Arrays.asList(id, "position", "z"), at.getBlockZ());
            if (!save(ctx)) {
                return;
            }
            ctx.msg("admin.position-set", "id", id, "x", at.getBlockX(), "y", at.getBlockY(), "z", at.getBlockZ(),
                    "monde", at.getWorld().getName());
        }

        @Override
        public List<String> complete(CommandContext ctx) {
            return completeIds(plugin, ctx);
        }
    }

    /** /totem config [fichier] */
    public static final class Config extends SubCommand {
        public Config(KoriaTotem plugin) {
            super(plugin, "config", true, 0, true);
        }

        @Override
        public void execute(CommandContext ctx) {
            if (!plugin.menus().openConfig(ctx.player, ctx.arg(0))) {
                ctx.msg("config-editor.unknown-file", "file", ctx.arg(0));
            }
        }

        @Override
        public List<String> complete(CommandContext ctx) {
            List<String> names = new ArrayList<String>();
            for (JsonFile file : plugin.configFiles()) {
                names.add(file.getName().replace(".json", ""));
            }
            return ctx.size() == 1 ? Text.complete(ctx.arg(0), names) : Collections.<String>emptyList();
        }
    }

    /** /totem save */
    public static final class Save extends SubCommand {
        public Save(KoriaTotem plugin) {
            super(plugin, "save", false, 0, true);
        }

        @Override
        public void execute(CommandContext ctx) {
            plugin.data().saveAsync();
            ctx.msg("general.saved");
        }
    }

    /** /totem reload */
    public static final class Reload extends SubCommand {
        public Reload(KoriaTotem plugin) {
            super(plugin, "reload", false, 0, true);
        }

        @Override
        public void execute(CommandContext ctx) {
            List<String> problems = plugin.reload();
            ctx.msg("general.reload-done", "totems", plugin.catalog().count());
            if (!problems.isEmpty()) {
                ctx.msg("general.reload-problems", "count", problems.size());
                for (String problem : problems) {
                    ctx.msg("general.reload-problem", "problem", problem);
                }
            }
        }
    }

    /** Écrit totems.json puis recharge la configuration. @return false en cas d'erreur */
    private static boolean save(CommandContext ctx) {
        try {
            ctx.plugin.totemsFile().save();
        } catch (IOException e) {
            ctx.msg("config-editor.save-error", "file", "totems.json", "error", e.getMessage());
            return false;
        }
        List<String> problems = ctx.plugin.reload();
        if (!problems.isEmpty()) {
            ctx.msg("general.reload-problems", "count", problems.size());
            for (String problem : problems) {
                ctx.msg("general.reload-problem", "problem", problem);
            }
        }
        return true;
    }
}
