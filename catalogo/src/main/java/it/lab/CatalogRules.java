// UD01 · Regola di business testata dalla build: stato di un prodotto in base alla giacenza.
package it.lab;
// Regole di business del catalogo: classe di sole funzioni statiche, non istanziabile.
public final class CatalogRules {
    // Costruttore privato: impedisce di creare istanze.
    private CatalogRules() {}
    // Restituisce lo stato di disponibilità in base alla giacenza.
    public static String status(int stock) {
        // Una giacenza negativa non è valida: lancia un'eccezione (verificato da CatalogRulesTest).
        if (stock < 0) throw new IllegalArgumentException("Stock negativo");
        // 0 pezzi = SOLD_OUT, altrimenti AVAILABLE.
        return stock == 0 ? "SOLD_OUT" : "AVAILABLE";
    }
}
