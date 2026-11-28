// UD01 · Test eseguiti da mvn verify durante la build multi-stage: se uno fallisce, l'immagine non viene creata.
package it.lab;
// Annotazione che segna un metodo come test.
import org.junit.jupiter.api.Test;
// Metodi di verifica: assertEquals (valore atteso) e assertThrows (eccezione attesa).
import static org.junit.jupiter.api.Assertions.*;
// Classe di test per CatalogRules.
class CatalogRulesTest {
    // Con giacenza 0 lo stato deve essere SOLD_OUT.
    @Test void zeroStockIsSoldOut() { assertEquals("SOLD_OUT",CatalogRules.status(0)); }
    // Con giacenza positiva lo stato deve essere AVAILABLE.
    @Test void positiveStockIsAvailable() { assertEquals("AVAILABLE",CatalogRules.status(1)); }
    // Con giacenza negativa deve essere lanciata IllegalArgumentException.
    @Test void negativeStockIsRejected() { assertThrows(IllegalArgumentException.class,()->CatalogRules.status(-1)); }
}
