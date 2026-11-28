// UD04 · Test del client di Catalogo: usano un finto server locale, senza rete esterna né cluster.
package it.lab.ordini;

// Metodi di verifica di JUnit.
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Server HTTP minimale incluso nel JDK, usato come finto Catalogo.
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
// Contenitore per passare un valore fuori dalla funzione del finto server.
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Prova il client con un finto Catalogo locale: nessuna rete esterna, nessun cluster. */
class CatalogoClientTest {
    // Risposta JSON finta di Catalogo con due prodotti.
    private static final String RISPOSTA = "{\"version\":\"v2\",\"requestId\":\"x\",\"products\":["
        + "{\"id\":\"P001\",\"name\":\"Corso Java\",\"stock\":5,\"status\":\"AVAILABLE\"},"
        + "{\"id\":\"P002\",\"name\":\"Corso OpenShift\",\"stock\":0,\"status\":\"ESAURITO\"}]}";

    // Primo test: il client legge il prodotto e inoltra il requestId.
    @Test
    void leggeIlProdottoEInoltraIlRequestId() throws Exception {
        // Qui il finto server salva l'intestazione ricevuta.
        AtomicReference<String> ricevuto = new AtomicReference<>();
        // Avvia un finto Catalogo su 127.0.0.1 con porta libera scelta dal sistema (0).
        HttpServer finto = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        // Risposta del finto server alla rotta /api/products.
        finto.createContext("/api/products", scambio -> {
            // Registra il requestId ricevuto.
            ricevuto.set(scambio.getRequestHeaders().getFirst("x-request-id"));
            // Prepara il corpo della risposta.
            byte[] corpo = RISPOSTA.getBytes(StandardCharsets.UTF_8);
            // Invia stato 200 e lunghezza del corpo.
            scambio.sendResponseHeaders(200, corpo.length);
            // Scrive il corpo.
            scambio.getResponseBody().write(corpo);
            // Chiude lo scambio.
            scambio.close();
        });
        // Avvia il finto server.
        finto.start();
        // try/finally: il server viene fermato anche se un controllo fallisce.
        try {
            // Crea il client puntando al finto server.
            var client = new CatalogoClient("http://127.0.0.1:" + finto.getAddress().getPort() + "/");
            // Cerca il prodotto P002.
            var p = client.prodotto("P002", "prova-123").orElseThrow();
            // Controlla il nome del prodotto.
            assertEquals("Corso OpenShift", p.nome());
            // Controlla la disponibilità.
            assertEquals(0, p.disponibili());
            // Controlla lo stato.
            assertEquals("SOLD_OUT", p.stato());
            // Controlla che il finto server abbia ricevuto il requestId inoltrato.
            assertEquals("prova-123", ricevuto.get());
            // Un prodotto inesistente deve dare un risultato vuoto.
            assertTrue(client.prodotto("P999", "prova-124").isEmpty());
        } finally {
            finto.stop(0);
        }
    }

    // Secondo test: se Catalogo non risponde l'errore è chiaro.
    @Test
    void catalogoSpentoDaUnErroreChiaro() {
        // Indirizzo con porta 9, su cui non ascolta nessuno.
        var client = new CatalogoClient("http://127.0.0.1:9");
        // Si attende l'eccezione CatalogoNonDisponibile.
        var e = assertThrows(CatalogoClient.CatalogoNonDisponibile.class, () -> client.prodotto("P001", "r"));
        // Il messaggio deve contenere "non raggiungibile".
        assertTrue(e.getMessage().contains("non raggiungibile"));
    }

    // Terzo test: riconosce gli id dei prodotti nel JSON.
    @Test
    void riconosceGliIdDeiProdotti() {
        // Con la risposta finta gli id attesi sono P001 e P002.
        assertEquals(java.util.List.of("P001", "P002"), CatalogoClient.idProdotti(RISPOSTA));
    }
}
