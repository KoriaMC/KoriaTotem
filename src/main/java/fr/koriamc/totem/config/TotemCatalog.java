package fr.koriamc.totem.config;

import fr.koriamc.totem.model.TotemDefinition;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Les totems de totems.json, dans l'ordre du fichier.
 */
public final class TotemCatalog {

    private final Map<String, TotemDefinition> totems = new LinkedHashMap<String, TotemDefinition>();
    private final List<String> problems = new ArrayList<String>();

    public void load(Configuration file, Settings settings) {
        totems.clear();
        problems.clear();
        for (String key : file.getKeys(false)) {
            ConfigurationSection s = file.getConfigurationSection(key);
            if (s == null) {
                problems.add("totems.json : " + key + " doit être un bloc { ... }");
                continue;
            }
            String id = key.toLowerCase(Locale.ROOT);
            if (totems.containsKey(id)) {
                problems.add("totems.json : deux totems portent le nom " + id);
                continue;
            }
            totems.put(id, TotemDefinition.read(id, s, settings, problems));
        }
    }

    /** @return le totem, ou null s'il n'existe pas */
    public TotemDefinition get(String id) {
        return id == null ? null : totems.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<TotemDefinition> all() {
        return totems.values();
    }

    public List<String> ids() {
        return new ArrayList<String>(totems.keySet());
    }

    public int count() {
        return totems.size();
    }

    public List<String> getProblems() {
        return problems;
    }
}
