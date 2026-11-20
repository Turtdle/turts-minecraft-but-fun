# Hosting a Modern Armory server

What this is for: an always-on server for **10–15 players**, **under $10 a month**, running Modern Armory.
Prices below are from public listings and reviews found in November 2026; check the provider's own pricing page
before buying, as several sources disagreed.

## What the server needs

- **Version:** Minecraft **1.21.1** with **NeoForge 21.1.267**. Hosts support 1.21.1 far better than 26.3, and both
  builds of the mod play the same.
- **Mods folder:** `modernarmory-1.21.1-<version>.jar` and `geckolib-neoforge-1.21.1-4.9.3.jar` from the
  [latest release](https://github.com/Turtdle/modern-armory/releases/latest). Every player needs the same two jars,
  which the installer sets up.
- **Memory:** 6 GB is comfortable for 10–15 players; 4 GB is the minimum. A fast CPU core matters more than many cores.
- **The host must let you upload your own mod jars.** Modern Armory isn't on CurseForge or Modrinth, so a host that
  only installs mods from those lists can't run it. As far as I know Aternos is one of those, and it also sleeps
  while nobody is online.

## Options

| Option | Cost for this server | Always on | Effort | Notes |
|---|---|---|---|---|
| **PebbleHost "Budget"** (recommended) | about **$6/mo for 6 GB** ($1/GB; 8 GB about $8) | yes | low: web panel, upload jars | Custom jars and NeoForge supported. Budget plans run on older CPUs; backups cost extra. "Premium" is $2.25/GB on faster CPUs, so 4 GB Premium (about $9) is the alternative if the budget node lags. |
| **Gravel Host budget** | about $5–6/mo for 6 GB ($0.90/GB) | yes | low | Cheapest per GB found; a smaller company, with fewer reviews. |
| **Oracle Cloud "Always Free"** | **$0** | yes | high: a Linux VM you set up yourself | Free ARM VM, now limited to 2 cores and 12 GB (cut from 4 / 24 GB in June 2026). Plenty for this server, but it needs a card on file for verification, Linux setup (Java 21, a service, firewall ports) and patience: popular regions often have no free capacity. |
| **Hostinger Game Panel, 8 GB** | $9.49/mo on a 24-month prepay ($227.76), renews at $14.99 | yes | medium | Under budget only with the long prepay. |
| **Hetzner Cloud CAX21** (4 ARM cores, 8 GB) | about €10.50/mo since the June 2026 price rise | yes | high (self-managed VM) | A little over budget now. |
| **This PC + a free tunnel (playit.gg)** | $0 | only while the PC is on | medium | Fine for testing; not "always on". |

**My recommendation:** PebbleHost Budget with 6 GB in the region closest to most players, about $6 a month. If it
stutters with everyone online, switch to Premium 4 GB at the same panel. If someone in the group is happy to run a
Linux server, Oracle's free tier is the $0 route.

## Setting it up on a hosting panel (PebbleHost or similar)

1. Order a Minecraft Java server with at least 6 GB.
2. In the panel, choose **NeoForge** for **Minecraft 1.21.1** (version 21.1.267). If the panel only offers other
   NeoForge 21.1 builds, any recent 21.1.x works.
3. Start it once so it creates its folders, then stop it.
4. Upload the two jars from the latest release into the server's `mods` folder.
5. In `server.properties`, keep `online-mode=true` (real Minecraft accounts). Turn on the whitelist and add your
   players (`/whitelist add <name>`), so strangers can't join.
6. Start the server and give your friends the address. They install with `ModernArmoryInstaller.exe`, choose
   Minecraft 1.21.1, open the launcher's **Modern Armory (1.21.1)** profile, then Multiplayer → Add Server.

To update the mod later: stop the server, replace `modernarmory-*.jar` in `mods` with the new one, and start it again.
Players run the installer again (it replaces their copy).

## Sources

- PebbleHost pricing: [pebblehost.com cheap Minecraft hosting](https://pebblehost.com/cheap-minecraft-server/),
  [modded hosting](https://pebblehost.com/minecraft-modded-server/),
  [2026 review (budget vs premium)](https://gameserverhosting.co.uk/reviews/pebblehost-review/)
- Budget host comparisons: [HostAdvice cheapest hosts, Nov 2026](https://hostadvice.com/game-server-hosting/best-minecraft/cheap/),
  [Wisehosting cheapest providers](https://wisehosting.com/blog/cheapest-minecraft-server-hosting-providers),
  [Gravel Host comparison](https://gravelhost.com/best-minecraft-server-hosting)
- Oracle free tier change: [linuxiac: Oracle cuts free A1 resources in half](https://linuxiac.com/oracle-quietly-cuts-free-tier-ampere-a1-resources-in-half/),
  [Oracle Cloud Minecraft setup guide (2026)](https://tech-insider.org/oracle-cloud-minecraft-server-free-tier-setup-2026/)
- Hetzner price change: [Hetzner price adjustment](https://docs.hetzner.com/general/infrastructure-and-availability/price-adjustment/)
- NeoForge server install: [NeoForge docs: installing a server](https://docs.neoforged.net/user/docs/server)
