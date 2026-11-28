// UD01 · Classe di avvio dell'applicazione Catalogo.
package it.lab;
// Classe che avvia l'applicazione Spring Boot.
import org.springframework.boot.SpringApplication;
// Annotazione che attiva la configurazione automatica.
import org.springframework.boot.autoconfigure.SpringBootApplication;
// Segna la classe di avvio: configurazione automatica e ricerca dei componenti nel package it.lab.
@SpringBootApplication
public class LabApplication {
    // Punto di ingresso del JAR (java -jar): avvia il server web.
    public static void main(String[] args) { SpringApplication.run(LabApplication.class, args); }
}
