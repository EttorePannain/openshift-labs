// UD04 · API REST di Ordini.
package it.lab.ordini;

// Richiesta HTTP in ingresso, per leggere le intestazioni.
import jakarta.servlet.http.HttpServletRequest;
// Collezioni e identificatori casuali.
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
// Scrittura dei log (visibili con oc logs).
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
// Inietta i valori di configurazione nei parametri del costruttore.
import org.springframework.beans.factory.annotation.Value;
// Classi Spring per gli stati HTTP, le rotte e le risposte di errore.
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// Classe che espone endpoint REST: le risposte sono JSON.
@RestController
// Tutte le rotte della classe cominciano con /api.
@RequestMapping("/api")
public class OrdersController {
    // Logger della classe.
    private static final Logger LOG = LoggerFactory.getLogger(OrdersController.class);

    /** Ordini di esempio: ognuno richiede un prodotto di Catalogo. */
    // Elenco fisso di tre ordini: il terzo richiede un prodotto che non esiste in Catalogo (P999).
    static final List<Map<String, Object>> ORDINI = List.of(
        Map.of("id", "O-101", "productId", "P001", "quantity", 2),
        Map.of("id", "O-102", "productId", "P002", "quantity", 1),
        Map.of("id", "O-103", "productId", "P999", "quantity", 1));

    // Messaggio e versione, letti dalla configurazione.
    private final String message;
    private final String version;
    // Client verso Catalogo.
    private final CatalogoClient catalogo;

    // Costruttore: Spring passa i valori di application.properties (a loro volta da variabili d'ambiente).
    public OrdersController(@Value("${app.message}") String message,
                            @Value("${app.version}") String version,
                            @Value("${app.catalogo-url}") String catalogoUrl) {
        this.message = message;
        this.version = version;
        // Crea il client verso l'indirizzo di Catalogo.
        this.catalogo = new CatalogoClient(catalogoUrl);
    }

    // GET /api/info: nome del servizio, versione, messaggio e indirizzo di Catalogo.
    @GetMapping("/info")
    public Map<String, Object> info() {
        return Map.of("service", "ordini", "version", version, "message", message, "catalogoUrl", catalogo.url());
    }

    // GET /api/orders: elenco degli ordini.
    @GetMapping("/orders")
    public List<Map<String, Object>> orders() {
        return ORDINI;
    }

    /** Dettaglio di un ordine, arricchito con i dati del prodotto letti da Catalogo. */
    // GET /api/orders/{id}: un ordine con i dati del prodotto letti da Catalogo.
    @GetMapping("/orders/{id}")
    public Map<String, Object> order(@PathVariable String id, HttpServletRequest request) {
        // Identificativo della richiesta: quello ricevuto o uno nuovo.
        String requestId = requestId(request);
        // Cerca l'ordine; se non esiste risponde 404.
        Map<String, Object> ordine = ORDINI.stream().filter(o -> o.get("id").equals(id)).findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ordine " + id + " inesistente"));
        // Id del prodotto richiesto dall'ordine.
        String productId = (String) ordine.get("productId");
        // Se Catalogo non è raggiungibile si risponde 502 (blocco catch più sotto).
        try {
            // Chiede il prodotto a Catalogo.
            var prodotto = catalogo.prodotto(productId, requestId);
            // Registra nel log richiesta, ordine, prodotto ed esito della ricerca.
            LOG.info("order requestId={} order={} product={} found={}", requestId, id, productId, prodotto.isPresent());
            // Parte dai dati dell'ordine e vi aggiunge requestId e prodotto.
            Map<String, Object> risposta = new LinkedHashMap<>(ordine);
            risposta.put("requestId", requestId);
            // Se il prodotto non esiste in Catalogo restituisce stato NON_IN_CATALOGO.
            risposta.put("product", prodotto.map(CatalogoClient::comeMappa)
                .orElse(Map.of("id", productId, "status", "NON_IN_CATALOGO")));
            return risposta;
        // Catalogo non disponibile: log di avviso e risposta 502 Bad Gateway.
        } catch (CatalogoClient.CatalogoNonDisponibile e) {
            LOG.warn("order requestId={} order={} error={}", requestId, id, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage());
        }
    }

    // Restituisce x-request-id se presente, altrimenti ne genera uno nuovo.
    private static String requestId(HttpServletRequest request) {
        String id = request.getHeader("x-request-id");
        return id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
    }
}
