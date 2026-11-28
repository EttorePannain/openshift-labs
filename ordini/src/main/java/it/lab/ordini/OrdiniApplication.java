// UD04 · Classe di avvio di Ordini.
package it.lab.ordini;

// Classi per avviare l'applicazione Spring Boot.
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Segna la classe di avvio: configurazione automatica e ricerca dei componenti nel package.
@SpringBootApplication
public class OrdiniApplication {
    // Punto di ingresso del JAR (java -jar): avvia il server web.
    public static void main(String[] args) {
        SpringApplication.run(OrdiniApplication.class, args);
    }
}
