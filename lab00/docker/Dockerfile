# UD01 · Dockerfile semplice: impacchetta un JAR già compilato fuori dall'immagine (non compila e non testa).
# Immagine di base: solo il runtime Java 17 (JRE), senza Maven né compilatore.
FROM eclipse-temurin:17-jre
# Cartella di lavoro dentro l'immagine: i comandi seguenti partono da qui.
WORKDIR /opt/catalogo
# Copia nell'immagine il JAR dal contesto di build. Se app/target/catalogo.jar non è nel contesto di build, la build si ferma qui.
COPY app/target/catalogo.jar catalogo.jar
# Esegue il processo come utente non root (UID 1001).
USER 1001
# Documenta la porta su cui l'applicazione ascolta; da sola non la pubblica.
EXPOSE 8080
# Comando di avvio: la forma a lista lancia java direttamente, senza shell intermedia, e il processo riceve i segnali di stop.
ENTRYPOINT ["java","-jar","/opt/catalogo/catalogo.jar"]
