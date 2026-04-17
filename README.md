# ProximityVoice Plugin

**ProximityVoice** is a lean Paper 1.21.11 plugin focused on proximity voice chat and nothing else. Although it was custom built for [minecraftoffline.net](https://www.minecraftoffline.net), any server can use it.

The plugin implements the [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) client protocol (by [henkelmax](https://github.com/henkelmax)) directly so the Simple Voice Chat server plugin is not required. Players will need the [client mod installed](#client-installation-players) though.

## Features
- **Proximity Voice Chat**: Players within configurable range hear each other in the same world.
- **Whispering**: A shorter configurable range for whisper mode.
- **Lag Friendly**: Audio routing and encryption run off the main thread to avoid tick impact.
- **Configurable**: Adjust voice range, whisper range, UDP port, and more in `config.yml`.

## Server Installation
1. Download the latest `ProximityVoice.jar` from [Releases](https://github.com/Jelly-Pudding/proximityvoice/releases/latest).
2. Place the `.jar` file in your Minecraft server's `plugins` folder.
3. Restart your server.
4. Open UDP port **24454** on your firewall and router. Voice data uses UDP so make sure the port is forwarded as UDP specifically.

## Client Installation (Players)

Players install the Simple Voice Chat client mod for Minecraft 1.21.11. ProximityVoice implements the same protocol server-side so the client connects to it without any extra setup.

1. Install [Fabric](https://fabricmc.net/use/installer/) for Minecraft 1.21.11.
2. Download the Simple Voice Chat mod for Minecraft 1.21.11 from [CurseForge](https://www.curseforge.com/minecraft/mc-mods/simple-voice-chat/files/all?page=1&pageSize=20&version=1.21.11).
3. Drop the mod `.jar` into your Fabric mods folder. On Windows this is typically `%AppData%\.minecraft\mods`.
4. Launch Minecraft and connect to the server as normal. Voice chat activates automatically.

> Players on Forge or NeoForge can use the equivalent Simple Voice Chat version for their mod loader.

## Configuration
```yaml
# ProximityVoice Configuration
# Open UDP port 'voice-port' on your firewall before starting the server.

voice-port: 24454
voice-distance: 48.0
whisper-distance: 6.0
keep-alive: 3000
mtu-size: 1024

# SVC client compatibility version. The 2.6.x client for Minecraft 1.21.11 uses 20.
# If voice chat fails silently, check server logs for "compat version" warnings.
compatibility-version: 20

# Set to true to log detailed UDP packet and routing information.
debug: false
```

| Key | Default | Description |
|---|---|---|
| `voice-port` | `24454` | UDP port the voice server listens on |
| `voice-distance` | `48.0` | Max range in blocks for normal voice |
| `whisper-distance` | `6.0` | Max range in blocks for whispering |
| `keep-alive` | `3000` | Milliseconds between keepalive pings. Players who go 30 seconds without responding are disconnected from voice. |
| `mtu-size` | `1024` | UDP packet size limit |
| `compatibility-version` | `20` | Must match what the client mod sends. The Simple Voice Chat client for Minecraft 1.21.11 uses 20. |
| `debug` | `false` | Log detailed UDP packet and routing information. |

## Port Forwarding

Voice data travels over **UDP**. If players can connect to your Minecraft server but voice chat does not work, the UDP port is almost certainly not open.

- Default port: **24454 UDP**
- Change it in `config.yml` under `voice-port`
- If you use a hosting provider, check their control panel for UDP port configuration

## Commands
- `/proximityvoice reload`: Reloads the plugin configuration (requires the `proximityvoice.admin` permission).

## Permissions
`proximityvoice.admin`: Allows reloading the plugin configuration (default: op).

## Troubleshooting

**Voice chat connects but I cannot hear anyone**

Make sure both players are in the same world and within `voice-distance` blocks of each other.

**Voice chat never connects**

Check that UDP port 24454 is open and forwarded as UDP. Voice data does not use TCP.

Check the server console for `compat version` warnings. If the number does not match `compatibility-version` in config, adjust it to match.

Set `debug: true` in `config.yml` and run `/proximityvoice reload` to enable detailed UDP logging. This will show whether authentication packets are arriving.

## Credits

The client mod used with this plugin is [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) by [henkelmax](https://github.com/henkelmax). ProximityVoice implements its protocol independently but would not exist without that work.

## Support Me
[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/K3K715TC1R)
