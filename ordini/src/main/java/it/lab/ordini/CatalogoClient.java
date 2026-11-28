// UD04 · Client che chiama Catalogo attraverso la rete.
package it.lab.ordini;

// Classi standard di Java per le chiamate HTTP, la durata, le collezioni e le espressioni regolari.
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chiama Catalogo attraverso la rete: GET {catalogoUrl}/api/products.
 * Inoltra l'identificativo della richiesta (x-request-id), così la stessa richiesta
 * si ritrova nei log di Ordini e di Catalogo.
 */
// Client HTTP verso Catalogo.
public class CatalogoClient {
    /** Risposta di Catalogo ridotta a ciò che serve a Ordini. */
    // Risposta di Catalogo ridotta ai quattro campi che servono (record: classe di soli dati, immutabile).
    public record Prodotto(String id, String nome, int disponibili, String stato) {}

    /** Catalogo non raggiungibile o risposta non valida. */
    // Eccezione propria, lanciata quando Catalogo non risponde o risponde male.
    public static class CatalogoNonDisponibile extends RuntimeException {
        // Costruttore: passa il messaggio alla classe base.
        public CatalogoNonDisponibile(String messaggio) { super(messaggio); }
    }

    // Espressione regolare che individua nel JSON un oggetto prodotto, cioè le graffe che contengono un campo "id".
    private static final Pattern PRODOTTO = Pattern.compile(
        "\\{[^{}]*\"id\"\\s*:\\s*\"([^\"]+)\"[^{}]*\\}");

    // Indirizzo base di Catalogo (nel cluster http://catalogo).
    private final String catalogoUrl;
    // Client HTTP condiviso; timeout di connessione di 3 secondi.
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    // Costruttore: memorizza l'indirizzo togliendo l'eventuale / finale.
    public CatalogoClient(String catalogoUrl) {
        this.catalogoUrl = catalogoUrl.endsWith("/") ? catalogoUrl.substring(0, catalogoUrl.length() - 1) : catalogoUrl;
    }

    // Restituisce l'indirizzo di Catalogo in uso.
    public String url() { return catalogoUrl; }

    // Cerca un prodotto per id chiamando Catalogo; il requestId viene inoltrato per poter seguire la richiesta nei log.
    public Optional<Prodotto> prodotto(String id, String requestId) {
        // Scarica l'elenco dei prodotti come testo JSON.
        String corpo = prodotti(requestId);
        // Prepara la ricerca dei prodotti nel testo.
        Matcher m = PRODOTTO.matcher(corpo);
        // Scorre i prodotti trovati.
        while (m.find()) {
            // Se l'id coincide con quello cercato...
            if (m.group(1).equals(id)) {
                // ...prende il testo JSON di quel prodotto...
                String json = m.group();
                // ...e ne ricava nome, disponibilità e stato.
                return Optional.of(new Prodotto(id, campo(json, "name"),
                    Integer.parseInt(campo(json, "stock")), campo(json, "status")));
            }
        }
        // Prodotto non trovato: risultato vuoto.
        return Optional.empty();
    }

    // Chiama GET {catalogoUrl}/api/products e restituisce il corpo della risposta.
    private String prodotti(String requestId) {
        // Costruisce la richiesta con timeout di 5 secondi e l'intestazione x-request-id.
        HttpRequest richiesta = HttpRequest.newBuilder(URI.create(catalogoUrl + "/api/products"))
            .timeout(Duration.ofSeconds(5)).header("x-request-id", requestId).GET().build();
        // Gli errori di rete vengono trasformati in CatalogoNonDisponibile.
        try {
            // Invia la richiesta e attende la risposta come testo.
            HttpResponse<String> risposta = client.send(richiesta, HttpResponse.BodyHandlers.ofString());
            // Se lo stato HTTP non è 200 segnala l'errore.
            if (risposta.statusCode() != 200) {
                throw new CatalogoNonDisponibile("Catalogo ha risposto " + risposta.statusCode());
            }
            // Risposta valida: restituisce il corpo.
            return risposta.body();
        // Chiamata interrotta: si ripristina lo stato del thread e si segnala l'errore.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CatalogoNonDisponibile("Chiamata a Catalogo interrotta");
        // Errore di rete (Catalogo spento, DNS, connessione): messaggio con l'indirizzo e il tipo di errore.
        } catch (java.io.IOException e) {
            throw new CatalogoNonDisponibile("Catalogo non raggiungibile su " + catalogoUrl + " (" + e.getClass().getSimpleName() + ")");
        }
    }

    // Estrae da un oggetto JSON il valore di un campo, testo o numero.
    private static String campo(String json, String nome) {
        // Espressione regolare: nome del campo, due punti e valore tra virgolette oppure numero.
        Matcher m = Pattern.compile("\"" + nome + "\"\\s*:\\s*(\"([^\"]*)\"|(-?\\d+))").matcher(json);
        // Campo assente: stringa vuota.
        if (!m.find()) return "";
        // Valore testuale se presente, altrimenti il numero.
        return m.group(2) != null ? m.group(2) : m.group(3);
    }

    // Elenca gli id di tutti i prodotti presenti nel JSON (usato dai test).
    static List<String> idProdotti(String corpo) {
        // Prepara la ricerca dei prodotti.
        Matcher m = PRODOTTO.matcher(corpo);
        // Lista in cui raccogliere gli id.
        java.util.ArrayList<String> ids = new java.util.ArrayList<>();
        // Aggiunge l'id di ogni prodotto trovato.
        while (m.find()) ids.add(m.group(1));
        // Restituisce l'elenco.
        return ids;
    }

    // Trasforma un prodotto nella mappa restituita nelle risposte JSON di Ordini.
    static Map<String, Object> comeMappa(Prodotto p) {
        return Map.of("id", p.id(), "name", p.nome(), "stock", p.disponibili(), "status", p.stato());
    }
}
