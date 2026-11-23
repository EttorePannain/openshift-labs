// UD01 · Marcatore di versione restituito da /api/info e /api/products.
package it.lab;
// Contiene la versione dell'applicazione mostrata nelle risposte.
public final class ReleaseMarker {
    // Costruttore privato: classe di sola costante.
    private ReleaseMarker() {}
    // Versione corrente dell'applicazione, restituita nelle risposte delle API.
    public static final String VERSION = "v2";
}
