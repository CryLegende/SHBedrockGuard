# SHBedrockGuard

Plugin Velocity pour SkyHand. Les joueurs Bedrock arrivent par Geyser et Floodgate avec un pseudo préfixé (`.Steve`).
Le réseau accepte les clients Java sans compte Mojang : sans ce plugin, n'importe qui pourrait prendre un pseudo commençant par un point.

Le plugin refuse à la connexion tout pseudo qui commence par le préfixe si l'UUID n'est pas un UUID Floodgate.
Les comptes Bedrock liés à un compte Java gardent le pseudo Java et ne sont pas concernés.

## Commande et permissions

| Commande | Description | Permission |
|---|---|---|
| `/shbedrockguard reload` | Relit `config.yml` et `messages.yml`. En cas d'erreur de lecture, l'ancienne configuration reste en place et le message donne le fichier et l'erreur. | `shbedrockguard.admin.reload` |

`shbedrockguard.admin` donne toutes les sous-commandes. Sur le proxy, les nœuds s'accordent dans le LuckPerms du proxy (il a sa propre base) ; la console n'a besoin de rien.

## Installation

Déposer le jar dans `plugins/` du proxy et redémarrer. La configuration se trouve dans `plugins/shbedrockguard/`.

## Compilation

`mvn package`, Java 25.
