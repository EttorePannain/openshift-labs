package it.lab;

// Librerie standard di Java per le chiamate HTTP, i file, i tempi e le collezioni.
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
// Richiesta HTTP in ingresso, per leggere le intestazioni.
import jakarta.servlet.http.HttpServletRequest;
// Scrittura dei log (visibili con oc logs).
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
// Inietta i valori di configurazione nei campi (annotazione @Value).
import org.springframework.beans.factory.annotation.Value;
// Classi Spring per costruire le risposte HTTP e gli errori.
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

// Classe che espone endpoint REST: le risposte vengono serializzate in JSON.
@RestController
public class LabController {
    // Logger della classe.
    private static final Logger LOG = LoggerFactory.getLogger(LabController.class);
    // Client HTTP per chiamare il backend; timeout di connessione di 3 secondi.
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    // Intestazioni di tracciamento distribuito da inoltrare al backend, per seguire una richiesta attraverso più servizi.
    private static final List<String> TRACE_HEADERS = List.of("traceparent","tracestate","b3","x-b3-traceid","x-b3-spanid","x-b3-parentspanid","x-b3-sampled","x-b3-flags");
    // Valori letti da application.properties, che a sua volta li prende dalle variabili d'ambiente (in OpenShift da ConfigMap e Secret).
    @Value("${app.role}") private String role;
    @Value("${app.message}") private String message;
    @Value("${app.demo-key}") private String demoKey;
    @Value("${app.backend-url}") private String backendUrl;
    @Value("${app.storage-enabled}") private boolean storageEnabled;
    @Value("${app.data-dir}") private String dataDir;

    // GET /api/info: ruolo, versione, messaggio e se la chiave segreta è configurata (mai il suo valore).
    @GetMapping("/api/info")
    public Map<String,Object> info() {
        return Map.of("role",role,"version",ReleaseMarker.VERSION,"message",message,"secretConfigured",!demoKey.isBlank());
    }
    // Restituisce l'identificativo della richiesta: quello ricevuto in x-request-id, oppure uno nuovo.
    private String requestId(HttpServletRequest request) {
        String id=request.getHeader("x-request-id");
        return id==null || id.isBlank() ? UUID.randomUUID().toString() : id;
    }
    // GET /api/products: elenco fisso di due prodotti con il loro stato; scrive un log con l'identificativo.
    @GetMapping("/api/products")
    public Map<String,Object> products(HttpServletRequest request) {
        String id=requestId(request);
        LOG.info("products requestId={} version={}",id,ReleaseMarker.VERSION);
        return Map.of("version",ReleaseMarker.VERSION,"requestId",id,"products",List.of(
            Map.of("id","P001","name","Corso Java","stock",5,"status",CatalogRules.status(5)),
            Map.of("id","P002","name","Corso OpenShift","stock",0,"status",CatalogRules.status(0))));
    }
    // GET /api/catalog: chiama il backend (/api/products) e ne inoltra la risposta; mostra la comunicazione tra due servizi.
    @GetMapping("/api/catalog")
    public ResponseEntity<String> catalog(HttpServletRequest request) {
        String id=requestId(request);
        // Se il backend non risponde si gestiscono gli errori qui sotto.
        try {
            // Costruisce la richiesta al backend con timeout di 5 secondi e lo stesso x-request-id.
            HttpRequest.Builder b=HttpRequest.newBuilder(URI.create(backendUrl+"/api/products"))
                .timeout(Duration.ofSeconds(5)).header("x-request-id",id).GET();
            // Copia le intestazioni di tracciamento presenti nella richiesta ricevuta.
            for(String name:TRACE_HEADERS) { String value=request.getHeader(name); if(value!=null) b.header(name,value); }
            // Invia la richiesta e attende la risposta come testo.
            var response=client.send(b.build(),HttpResponse.BodyHandlers.ofString());
            // Registra l'esito della chiamata al backend.
            LOG.info("catalog requestId={} upstreamStatus={}",id,response.statusCode());
            // Restituisce al chiamante stato, tipo di contenuto e corpo ricevuti dal backend.
            return ResponseEntity.status(response.statusCode()).header("Content-Type",response.headers().firstValue("content-type").orElse("text/plain"))
                .header("x-request-id",id).body(response.body());
        // Chiamata interrotta: si ripristina lo stato del thread e si risponde 503.
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Chiamata interrotta");
        // Qualsiasi altro errore (backend spento, DNS, timeout): log e risposta 502 Bad Gateway.
        } catch(Exception e) {
            LOG.warn("catalog requestId={} error={}",id,e.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Backend non raggiungibile");
        }
    }
    // Percorso del file del contatore; se lo storage è disabilitato risponde 404.
    private Path counterPath() {
        if(!storageEnabled) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Storage demo disabilitato");
        return Path.of(dataDir,"counter.txt");
    }
    // GET /api/counter: legge il valore dal file (0 se il file non esiste). synchronized: una richiesta alla volta.
    @GetMapping("/api/counter")
    public synchronized Map<String,Long> readCounter() throws java.io.IOException {
        Path p=counterPath();
        return Map.of("counter",Files.exists(p)?Long.parseLong(Files.readString(p).trim()):0L);
    }
    // POST /api/counter: incrementa il contatore e lo salva su disco (usato nella UD06 per mostrare la persistenza).
    @PostMapping("/api/counter")
    public synchronized Map<String,Long> incrementCounter() throws java.io.IOException {
        Path p=counterPath();Files.createDirectories(p.getParent());
        long value=Files.exists(p)?Long.parseLong(Files.readString(p).trim())+1:1;
        // Scrive prima in un file temporaneo e poi lo sposta sul file definitivo: la scrittura è completa o assente, mai a metà.
        Path tmp=Files.createTempFile(p.getParent(),"counter-",".tmp");
        Files.writeString(tmp,Long.toString(value));
        Files.move(tmp,p,StandardCopyOption.REPLACE_EXISTING);
        return Map.of("counter",value);
    }
}
