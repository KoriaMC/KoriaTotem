package fr.koriamc.totem.reward;

import fr.koriamc.totem.KoriaTotem;
import fr.koriamc.totem.model.PlayerStats;
import fr.koriamc.totem.util.Items;
import fr.koriamc.totem.util.MaterialSet;
import fr.koriamc.totem.util.Sounds;
import fr.koriamc.totem.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Récompenses écrites sous forme d'actions dans totems.json :
 * <ul>
 *   <li>POINTS:n : points de faction (KoriaFactions), une seule fois pour la faction</li>
 *   <li>ANNONCE:texte : message à tout le serveur, une seule fois</li>
 *   <li>ARGENT:n : argent Vault</li>
 *   <li>ITEM:matériau[:data]:quantité : objet dans l'inventaire (au sol s'il est plein)</li>
 *   <li>CONSOLE:commande : commande exécutée par la console</li>
 *   <li>COMMANDE:commande : commande exécutée par le joueur</li>
 *   <li>MESSAGE:texte : message au joueur ([actionbar] et [title] acceptés)</li>
 *   <li>SON:nom[:volume:pitch] : son joué au joueur</li>
 * </ul>
 * Les actions personnelles d'un joueur hors ligne sont gardées et exécutées à sa connexion.
 */
public final class RewardService {

    private final KoriaTotem plugin;

    public RewardService(KoriaTotem plugin) {
        this.plugin = plugin;
    }

    private static String type(String action) {
        int colon = action.indexOf(':');
        return (colon < 0 ? action : action.substring(0, colon)).trim().toUpperCase(Locale.ROOT);
    }

    private static String value(String action) {
        int colon = action.indexOf(':');
        String v = colon < 0 ? "" : action.substring(colon + 1).trim();
        return v.startsWith("/") ? v.substring(1) : v;
    }

    /** Total des points de faction d'une liste de récompenses. */
    public long points(List<String> rewards) {
        long total = 0;
        for (String action : rewards) {
            if (type(action).equals("POINTS")) {
                Long n = parseLong(value(action));
                total += n == null ? 0 : n;
            }
        }
        return total;
    }

    // ============================================================= Affichage

    /** Lignes lisibles des récompenses (points, argent, objets). */
    public List<String> describe(List<String> rewards) {
        List<String> out = new ArrayList<String>();
        for (String action : rewards) {
            String value = value(action);
            if (type(action).equals("POINTS")) {
                out.add(plugin.msg().text("rewards.describe.points", "quantite", Text.number(parseDouble(value), 0)));
            } else if (type(action).equals("ARGENT") || type(action).equals("MONEY")) {
                out.add(plugin.msg().text("rewards.describe.money", "quantite", Text.number(parseDouble(value), 2)));
            } else if (type(action).equals("ITEM")) {
                ItemSpec spec = ItemSpec.parse(value);
                if (spec != null) {
                    out.add(plugin.msg().text("rewards.describe.item", "quantite", spec.amount, "objet", spec.displayName()));
                }
            }
        }
        if (out.isEmpty()) {
            out.add(plugin.msg().text("rewards.none"));
        }
        return out;
    }

    // ============================================================ Distribution

    /**
     * Distribue des récompenses.
     *
     * @param factionId  faction qui reçoit les POINTS (null : points perdus)
     * @param recipients joueurs qui reçoivent les récompenses personnelles
     * @param kv         variables des textes et commandes ({joueur} est ajouté pour chacun)
     * @return les points réellement crédités
     */
    public long grant(String factionId, Collection<UUID> recipients, List<String> rewards, Object... kv) {
        long credited = 0;
        List<String> personal = new ArrayList<String>();
        for (String action : rewards) {
            String type = type(action);
            if (type.equals("POINTS")) {
                Long n = parseLong(value(action));
                if (n != null && factionId != null) {
                    credited += plugin.factionHook().addPoints(factionId, n);
                }
            } else if (type.equals("ANNONCE") || type.equals("BROADCAST")) {
                String text = plugin.msg().format(value(action), kv);
                for (Player p : Bukkit.getOnlinePlayers()) {
                    plugin.msg().sendLine(p, text);
                }
                Bukkit.getConsoleSender().sendMessage(text);
            } else {
                personal.add(action);
            }
        }
        if (personal.isEmpty()) {
            return credited;
        }
        for (UUID uuid : recipients) {
            Player player = Bukkit.getPlayer(uuid);
            PlayerStats ps = plugin.data().findPlayer(uuid);
            String name = player != null ? player.getName()
                    : ps != null ? ps.getName() : Bukkit.getOfflinePlayer(uuid).getName();
            Object[] vars = with(kv, "joueur", name, "player", name);
            for (String action : personal) {
                String resolved = Text.replace(action, vars);
                if (player != null && player.isOnline()) {
                    give(player, resolved);
                } else {
                    plugin.data().player(uuid, name).getPending().add(resolved);
                }
            }
        }
        return credited;
    }

    /** Exécute les récompenses reçues pendant l'absence du joueur. */
    public void deliverPending(Player player, PlayerStats stats) {
        if (stats.getPending().isEmpty()) {
            return;
        }
        List<String> actions = new ArrayList<String>(stats.getPending());
        stats.getPending().clear();
        for (String action : actions) {
            give(player, action);
        }
        plugin.msg().send(player, "rewards.pending");
    }

    /** Exécute une action personnelle déjà résolue. */
    @SuppressWarnings("deprecation")
    public void give(Player player, String action) {
        String value = value(action);
        String type = type(action);
        if (type.equals("ARGENT") || type.equals("MONEY")) {
            Double amount = Text.parseDouble(value);
            if (amount != null && amount > 0 && !plugin.deposit(player, amount)) {
                plugin.getLogger().warning("Récompense ARGENT:" + value + " non donnée à " + player.getName()
                        + " : aucune économie Vault disponible.");
            }
        } else if (type.equals("ITEM")) {
            ItemSpec spec = ItemSpec.parse(value);
            if (spec == null) {
                plugin.getLogger().warning("Récompense ITEM invalide : " + action);
                return;
            }
            ItemStack item = new ItemStack(spec.id, spec.amount, (short) Math.max(0, spec.data));
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
            if (!overflow.isEmpty()) {
                for (ItemStack rest : overflow.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), rest);
                }
                plugin.msg().send(player, "rewards.inventory-full");
            }
        } else if (type.equals("CONSOLE")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), value);
        } else if (type.equals("COMMANDE") || type.equals("COMMAND")) {
            player.performCommand(value);
        } else if (type.equals("MESSAGE")) {
            plugin.msg().sendLine(player, plugin.msg().format(value));
        } else if (type.equals("SON") || type.equals("SOUND")) {
            Sounds.play(player, value);
        } else {
            plugin.getLogger().warning("Récompense inconnue : " + action);
        }
    }

    /** Ajoute des variables en tête : elles l'emportent sur celles de même nom déjà présentes. */
    private static Object[] with(Object[] kv, Object... extra) {
        Object[] all = Arrays.copyOf(extra, extra.length + kv.length);
        System.arraycopy(kv, 0, all, extra.length, kv.length);
        return all;
    }

    private static Long parseLong(String s) {
        Double d = Text.parseDouble(s);
        return d == null ? null : (long) Math.floor(d);
    }

    private static double parseDouble(String s) {
        Double d = Text.parseDouble(s);
        return d == null ? 0 : d;
    }

    /** ITEM:matériau[:data]:quantité */
    @SuppressWarnings("deprecation")
    private static final class ItemSpec {
        final int id;
        final int data;
        final int amount;

        private ItemSpec(int id, int data, int amount) {
            this.id = id;
            this.data = data;
            this.amount = amount;
        }

        static ItemSpec parse(String value) {
            String[] parts = value.split(":");
            String material = parts[0];
            int amount = 1;
            if (parts.length == 2) {
                Integer n = Text.parseInt(parts[1]);
                amount = n == null ? 1 : n;
            } else if (parts.length >= 3) {
                material = parts[0] + ":" + parts[1];
                Integer n = Text.parseInt(parts[2]);
                amount = n == null ? 1 : n;
            }
            int[] parsed = MaterialSet.parse(material);
            if (parsed == null) {
                return null;
            }
            return new ItemSpec(parsed[0], parsed[1], Math.max(1, Math.min(64 * 36, amount)));
        }

        String displayName() {
            Material m = Material.getMaterial(id);
            String name = m == null ? String.valueOf(id) : m.name().toLowerCase(Locale.ROOT).replace('_', ' ');
            return Items.capitalize(name);
        }
    }
}
