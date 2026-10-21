package fr.koriamc.totem.storage;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Copie sérialisée (JSON) de toutes les données, préparée sur le thread
 * principal puis écrite de façon asynchrone.
 */
public final class DataSnapshot {

    public final Map<String, String> players = new LinkedHashMap<String, String>();
    public final Map<String, String> factions = new LinkedHashMap<String, String>();
    /** État du cycle hebdomadaire (une seule entrée). */
    public final Map<String, String> meta = new LinkedHashMap<String, String>();
}
