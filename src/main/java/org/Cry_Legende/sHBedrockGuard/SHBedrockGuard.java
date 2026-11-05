package org.Cry_Legende.sHBedrockGuard;

import com.google.inject.Inject;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Plugin(
    id = "shbedrockguard",
    name = "SHBedrockGuard",
    version = "1.0",
    description = "Reserve le prefixe des pseudos Bedrock aux joueurs Bedrock",
    authors = {"Cry_Legende"}
)
public class SHBedrockGuard {

    private final Logger logger;
    private final Path dataFolder;

    private boolean actif;
    private String prefixe;
    private Component refus;

    @Inject
    public SHBedrockGuard(Logger logger, @DataDirectory Path dataFolder) {
        this.logger = logger;
        this.dataFolder = dataFolder;
    }

    @Subscribe
    public void onInit(ProxyInitializeEvent event) {
        try {
            Map<String, Object> config = charger("config.yml");
            Map<String, Object> messages = charger("messages.yml");
            Object pseudos = config.get("pseudos");
            if (!(pseudos instanceof Map<?, ?> section)) {
                throw new IOException("section pseudos absente de config.yml");
            }
            actif = Boolean.TRUE.equals(section.get("actif"));
            prefixe = String.valueOf(section.get("prefixe"));
            refus = LegacyComponentSerializer.legacyAmpersand().deserialize(String.valueOf(messages.get("refus")));
            logger.info("Prefixe {} reserve aux joueurs Bedrock (actif : {})", prefixe, actif);
        } catch (IOException | RuntimeException e) {
            actif = false;
            logger.error("Configuration illisible, garde desactivee", e);
        }
    }

    // Floodgate a deja pose le profil au moment de LoginEvent.
    @Subscribe(order = PostOrder.EARLY)
    public void onLogin(LoginEvent event) {
        if (!actif || !event.getResult().isAllowed()) {
            return;
        }
        Player player = event.getPlayer();
        String pseudo = player.getUsername();
        if (!pseudo.regionMatches(true, 0, prefixe, 0, prefixe.length())) {
            return;
        }
        // Un joueur Floodgate non lie a un UUID dont la moitie haute vaut zero.
        if (player.getUniqueId().getMostSignificantBits() != 0L) {
            event.setResult(ResultedEvent.ComponentResult.denied(refus));
            logger.info("Connexion Java refusee sur le pseudo {}", pseudo);
        }
    }

    private Map<String, Object> charger(String nom) throws IOException {
        Files.createDirectories(dataFolder);
        Path fichier = dataFolder.resolve(nom);
        if (Files.notExists(fichier)) {
            try (InputStream defaut = getClass().getResourceAsStream("/" + nom)) {
                if (defaut == null) {
                    throw new IOException(nom + " absent du jar");
                }
                Files.copy(defaut, fichier);
            }
        }
        try (Reader lecteur = Files.newBufferedReader(fichier)) {
            Map<String, Object> contenu = new Yaml().load(lecteur);
            if (contenu == null) {
                throw new IOException(nom + " vide");
            }
            return contenu;
        }
    }
}
