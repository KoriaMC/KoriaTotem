package fr.koriamc.totem.command.impl;

import fr.koriamc.totem.KoriaTotem;
import fr.koriamc.totem.command.CommandContext;
import fr.koriamc.totem.command.SubCommand;
import fr.koriamc.totem.model.MetaState;
import fr.koriamc.totem.model.TotemDefinition;
import fr.koriamc.totem.model.TotemSession;
import fr.koriamc.totem.util.Text;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;

/**
 * Commandes des joueurs : aide, menu, liste, classement et état de l'évènement.
 */
public final class PlayerCommands {

    private PlayerCommands() {
    }

    /** /totem help */
    public static final class Help extends SubCommand {
        public Help(KoriaTotem plugin) {
            super(plugin, "help", false, 0, false);
        }

        @Override
        public void execute(CommandContext ctx) {
            ctx.msg("help.header");
            for (SubCommand sub : plugin.commands().visible(ctx.sender)) {
                ctx.msg("help.line", "command", sub.getId(), "usage", sub.getUsage(), "description", sub.getDescription());
            }
            ctx.msg("help.footer");
        }
    }

    /** /totem menu */
    public static final class OpenMenu extends SubCommand {
        public OpenMenu(KoriaTotem plugin) {
            super(plugin, "menu", true, 0, false);
        }

        @Override
        public void execute(CommandContext ctx) {
            plugin.menus().openList(ctx.player);
        }
    }

    /** /totem list : les totems et leur planning dans le chat */
    public static final class ListTotems extends SubCommand {
        public ListTotems(KoriaTotem plugin) {
            super(plugin, "list", false, 0, false);
        }

        @Override
        public void execute(CommandContext ctx) {
            if (plugin.catalog().count() == 0) {
                ctx.msg("totem.none-configured");
                return;
            }
            ctx.msg("totem.list-header", "total", plugin.catalog().count());
            for (TotemDefinition def : plugin.catalog().all()) {
                TotemSession session = plugin.totems().get(def.getId());
                MetaState.Winner last = plugin.data().meta().getLast(def.getId());
                ctx.msg("totem.list-line",
                        "totem", Text.color(def.getName()),
                        "id", def.getId(),
                        "etat", session == null ? plugin.msg().text("display.state-waiting") : plugin.display().state(session),
                        "restants", session == null ? def.getHeight() : session.getRemaining(),
                        "total", def.getHeight(),
                        "planning", plugin.display().schedule(def.getSchedule()),
                        "prochain", plugin.msg().duration(def.getSchedule()
                                .millisUntilNext(ZonedDateTime.now(plugin.settings().zone))),
                        "dernier", last == null ? plugin.msg().text("values.none") : last.getTag());
            }
            ctx.msg("totem.list-footer");
        }
    }

    /** /totem top [totem] */
    public static final class Top extends SubCommand {
        public Top(KoriaTotem plugin) {
            super(plugin, "top", true, 0, false);
        }

        @Override
        public void execute(CommandContext ctx) {
            plugin.menus().openTop(ctx.player, ctx.arg(0));
        }

        @Override
        public List<String> complete(CommandContext ctx) {
            return ctx.size() == 1 ? Text.complete(ctx.arg(0), plugin.catalog().ids()) : Collections.<String>emptyList();
        }
    }

    /** /totem info : totem en cours ou prochaine apparition */
    public static final class Info extends SubCommand {
        public Info(KoriaTotem plugin) {
            super(plugin, "info", false, 0, false);
        }

        @Override
        public void execute(CommandContext ctx) {
            if (plugin.totems().isRunning()) {
                for (TotemSession session : plugin.totems().sessions()) {
                    plugin.msg().send(ctx.sender, "totem.info-running", plugin.display().vars(session));
                }
                return;
            }
            TotemDefinition next = plugin.totems().nextScheduled();
            if (next == null) {
                ctx.msg("totem.info-none");
                return;
            }
            ctx.msg("totem.info-next", "totem", Text.color(next.getName()), "id", next.getId(),
                    "temps", plugin.msg().duration(plugin.totems().nextScheduledMillis()),
                    "planning", plugin.display().schedule(next.getSchedule()),
                    "x", next.getX(), "y", next.getY(), "z", next.getZ(), "monde", next.getWorldName());
        }
    }
}
