package org.Cry_Legende.sHBedrockGuard;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Plugin(
    id = "shbedrockguard",
    name = "SHBedrockGuard",
    version = "1.1",
    description = "Reserve le prefixe des pseudos Bedrock aux joueurs Bedrock",
    authors = {"Cry_Legende"}
)
public class SHBedrockGuard {

    private static final String ADMIN = "shbedrockguard.admin";
    private static final String RELOAD = "shbedrockguard.admin.reload";
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    /**
     * Réglages du dernier chargement valide. Le reload les remplace d'un bloc : un login ne voit jamais un mélange
     * d'ancienne et de nouvelle configuration.
     */
    private record Settings(boolean actif, String prefixe, Component refus, Map<String, String> messages) {
    }

    /** Fichier et cause d'un chargement refusé. */
    private static final class ConfigException extends Exception {
        private final String fichier;

        ConfigException(String fichier, String message, Throwable cause) {
            super(message, cause);
            this.fichier = fichier;
        }
    }

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataFolder;

    private volatile Settings settings = new Settings(false, ".", Component.empty(), Map.of());

    @Inject
    public SHBedrockGuard(ProxyServer proxy, Logger logger, @DataDirectory Path dataFolder) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataFolder = dataFolder;
    }

    @Subscribe
    public void onInit(ProxyInitializeEvent event) {
        CommandMeta meta = proxy.getCommandManager().metaBuilder("shbedrockguard").plugin(this).build();
        proxy.getCommandManager().register(meta, new AdminCommand());
        try {
            Settings loaded = load();
            settings = loaded;
            logger.info("Prefixe {} reserve aux joueurs Bedrock (actif : {})", loaded.prefixe(), loaded.actif());
        } catch (ConfigException e) {
            // Les textes du jar servent au moins à répondre à /shbedrockguard reload
            settings = new Settings(false, ".", Component.empty(), jarMessages());
            logger.error("Configuration illisible ({} : {}), garde desactivee", e.fichier, e.getMessage(), e);
        }
    }

    // Floodgate a deja pose le profil au moment de LoginEvent.
    @Subscribe(order = PostOrder.EARLY)
    public void onLogin(LoginEvent event) {
        Settings current = settings;
        if (!current.actif() || !event.getResult().isAllowed()) {
            return;
        }
        Player player = event.getPlayer();
        String pseudo = player.getUsername();
        String prefixe = current.prefixe();
        if (!pseudo.regionMatches(true, 0, prefixe, 0, prefixe.length())) {
            return;
        }
        // Un joueur Floodgate non lie a un UUID dont la moitie haute vaut zero.
        if (player.getUniqueId().getMostSignificantBits() != 0L) {
            event.setResult(ResultedEvent.ComponentResult.denied(current.refus()));
            logger.info("Connexion Java refusee sur le pseudo {}", pseudo);
        }
    }

    // ------------------------------------------------------------------ commande

    private final class AdminCommand implements SimpleCommand {

        @Override
        public void execute(Invocation invocation) {
            CommandSource source = invocation.source();
            String[] args = invocation.arguments();
            if (args.length > 0 && args[0].equalsIgnoreCase("reload") && canReload(source)) {
                reload(source);
                return;
            }
            send(source, settings.messages(), "usage", "{subcommands}", String.join("|", usable(source)));
        }

        // Sans aucun droit d'admin, Velocity ne l'exécute pas et passe la commande au serveur de jeu
        @Override
        public boolean hasPermission(Invocation invocation) {
            return !usable(invocation.source()).isEmpty();
        }

        @Override
        public List<String> suggest(Invocation invocation) {
            String[] args = invocation.arguments();
            if (args.length > 1) {
                return List.of();
            }
            String typed = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return usable(invocation.source()).stream().filter(name -> name.startsWith(typed)).toList();
        }
    }

    /** Sous-commandes que l'expéditeur a le droit d'utiliser. */
    private List<String> usable(CommandSource source) {
        return canReload(source) ? List.of("reload") : List.of();
    }

    // Velocity ne propage pas les enfants d'un nœud : le parent est testé aussi
    private boolean canReload(CommandSource source) {
        return source.hasPermission(RELOAD) || source.hasPermission(ADMIN);
    }

    /**
     * Relit config.yml et messages.yml en entier. En cas d'erreur, la configuration en place est gardée : la garde
     * n'est jamais coupée par une faute de frappe.
     */
    private void reload(CommandSource source) {
        try {
            Settings next = load();
            settings = next;
            logger.info("Configuration rechargee : prefixe {} (actif : {})", next.prefixe(), next.actif());
            send(source, next.messages(), "reload-ok",
                "{prefixe}", next.prefixe(), "{etat}", next.actif() ? "&aactive" : "&cinactive");
        } catch (ConfigException e) {
            logger.error("Rechargement refuse, ancienne configuration gardee ({} : {})", e.fichier, e.getMessage());
            send(source, settings.messages(), "reload-error", "{fichier}", e.fichier, "{erreur}", e.getMessage());
        }
    }

    private void send(CommandSource source, Map<String, String> messages, String key, String... replacements) {
        String text = messages.get(key);
        if (text == null) {
            text = jarMessages().getOrDefault(key, key);
        }
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            text = text.replace(replacements[i], replacements[i + 1]);
        }
        source.sendMessage(LEGACY.deserialize(text));
    }

    // ------------------------------------------------------------------ chargement

    /** Lit les deux fichiers et valide tout avant de rendre les nouveaux réglages. */
    private Settings load() throws ConfigException {
        Map<String, Object> config = read("config.yml");
        Map<String, String> messages = jarMessages();
        read("messages.yml").forEach((key, value) -> {
            if (value != null) {
                messages.put(key, String.valueOf(value));
            }
        });
        if (!(config.get("pseudos") instanceof Map<?, ?> section)) {
            throw new ConfigException("config.yml", "section pseudos absente", null);
        }
        Object prefixe = section.get("prefixe");
        // Un prefixe vide correspondrait a tous les pseudos
        if (prefixe == null || String.valueOf(prefixe).isEmpty()) {
            throw new ConfigException("config.yml", "pseudos.prefixe absent ou vide", null);
        }
        String refus = messages.get("refus");
        if (refus == null) {
            throw new ConfigException("messages.yml", "refus absent", null);
        }
        return new Settings(Boolean.TRUE.equals(section.get("actif")), String.valueOf(prefixe),
            LEGACY.deserialize(refus), Map.copyOf(messages));
    }

    /** Textes du messages.yml du jar : valeurs par défaut des clés absentes du fichier du serveur. */
    private Map<String, String> jarMessages() {
        Map<String, String> result = new HashMap<>();
        try (InputStream in = getClass().getResourceAsStream("/messages.yml")) {
            if (in != null) {
                Object parsed = new Yaml().load(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                if (parsed instanceof Map<?, ?> map) {
                    map.forEach((key, value) -> {
                        if (key != null && value != null) {
                            result.put(String.valueOf(key), String.valueOf(value));
                        }
                    });
                }
            }
        } catch (IOException | RuntimeException e) {
            logger.error("messages.yml du jar illisible", e);
        }
        return result;
    }

    private Map<String, Object> read(String nom) throws ConfigException {
        try {
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
                    throw new ConfigException(nom, "fichier vide", null);
                }
                return contenu;
            }
        } catch (IOException e) {
            throw new ConfigException(nom, e.getMessage(), e);
        } catch (RuntimeException e) {
            // YAMLException : première ligne seulement, le détail complet est dans la console
            String message = String.valueOf(e.getMessage());
            throw new ConfigException(nom, message.lines().findFirst().orElse(message), e);
        }
    }
}
