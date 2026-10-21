package fr.koriamc.totem.hook;

import fr.koriamc.totem.KoriaTotem;
import fr.koriamc.totem.model.TotemDefinition;
import fr.koriamc.totem.model.TotemSession;
import fr.koriamc.totem.util.Text;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Pont facultatif vers le système de GUI serveur de KoriaMC (fr.koriamc.gui.KoriaGuiAPI,
 * fourni par le serveur lui-même), par réflexion.
 * <p>
 * Les valeurs sont publiées en variables globales, utilisables dans les layouts du
 * client avec %gvar_totem_nom%, %gvar_totem_restants%, %gvar_totem_faction%...
 */
public final class GuiHook {

    /** Variables de l'évènement publiées telles quelles (voir Display.vars). */
    private static final String[] PUBLISHED = {"id", "x", "y", "z", "restants", "total", "pourcentage", "faction",
            "joueur", "meilleur", "meilleur_blocs"};

    private final KoriaTotem plugin;
    private final Map<String, String> pushed = new HashMap<String, String>();
    private boolean checked;
    private Method setGlobalVar;

    public GuiHook(KoriaTotem plugin) {
        this.plugin = plugin;
    }

    private boolean available() {
        if (!plugin.settings().guiIntegration) {
            return false;
        }
        if (!checked) {
            checked = true;
            try {
                setGlobalVar = Class.forName("fr.koriamc.gui.KoriaGuiAPI")
                        .getMethod("setGlobalVar", String.class, String.class);
                plugin.getLogger().info("Interface KoriaMC détectée : variables %gvar_" + plugin.settings().guiPrefix
                        + "...% publiées pendant les évènements.");
            } catch (Throwable t) {
                setGlobalVar = null;
            }
        }
        return setGlobalVar != null;
    }

    /** Publie l'état de l'évènement en cours. */
    public void update(TotemSession session) {
        if (!available()) {
            return;
        }
        set("actif", "true");
        set("nom", Text.strip(session.getDefinition().getName()));
        Object[] vars = plugin.display().vars(session);
        for (int i = 0; i + 1 < vars.length; i += 2) {
            for (String key : PUBLISHED) {
                if (key.equals(vars[i])) {
                    set(key, Text.strip(String.valueOf(vars[i + 1])));
                }
            }
        }
    }

    /** Remet les variables à vide à la fin de l'évènement. */
    public void clear(TotemDefinition def) {
        if (!available() || plugin.totems().isRunning()) {
            return;
        }
        set("actif", "false");
        set("nom", "");
        for (String key : PUBLISHED) {
            set(key, "");
        }
    }

    /** N'envoie que les valeurs qui ont changé (chaque écriture rafraîchit les clients). */
    private void set(String key, String value) {
        String full = plugin.settings().guiPrefix + key;
        if (value.equals(pushed.get(full))) {
            return;
        }
        pushed.put(full, value);
        try {
            setGlobalVar.invoke(null, full, value);
        } catch (Throwable t) {
            setGlobalVar = null;
            plugin.getLogger().warning("Interface KoriaMC : publication impossible (" + t + ")");
        }
    }
}
