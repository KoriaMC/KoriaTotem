package fr.koriamc.totem.storage;

/**
 * Support de stockage. Toutes les méthodes sont appelées depuis un seul thread à
 * la fois (chargement au démarrage, puis file d'écriture dédiée).
 */
public interface Storage {

    String getName();

    void init() throws Exception;

    /** Charge toutes les données sous forme de JSON. */
    DataSnapshot load() throws Exception;

    /** Écrit les données ; les implémentations peuvent n'écrire que les différences. */
    void save(DataSnapshot snapshot) throws Exception;

    void close();
}
