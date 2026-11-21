#!/usr/bin/env bash
# Sets up (or updates) a Modern Armory server on an Ubuntu 22.04 / 24.04 machine (x86_64 or ARM64, e.g. Oracle
# Cloud's free Ampere VM), as root:
#
#   sudo bash setup-server.sh --accept-eula [--mc 1.21.1] [--ram 8G] [--whitelist "Alice,Bob"] [--motd "text"]
#   sudo modern-armory-setup --update       # later: newest mod release, then restart
#
# --accept-eula  you accept Minecraft's EULA (https://aka.ms/MinecraftEULA); the server won't start without it
# --mc           Minecraft version to run (one of the release's install.json targets; default 1.21.1)
# --ram          Java heap (default: the machine's RAM minus 2 GB, between 2G and 10G)
# --whitelist    players allowed to join (comma separated); more later with: mc-cmd "whitelist add Name"
#
# What you get:
#   /opt/minecraft/server           NeoForge server, mods/ with Modern Armory + GeckoLib from the latest GitHub release
#   minecraft.service               starts on boot, restarts after a crash; online mode + whitelist on
#   mc-cmd "<command>"              runs a console command (RCON, localhost only), e.g. mc-cmd "op Alice"
#   modern-armory-backup.timer      world backup every day at 04:00 to /opt/minecraft/backups (last 7 kept)
#   firewall                        only SSH and the Minecraft port (25565/tcp) open
set -euo pipefail

REPO="Turtdle/modern-armory"
BASE=/opt/minecraft
DIR=$BASE/server
MC="1.21.1"; RAM=""; WHITELIST=""; MOTD="Modern Armory"; EULA=0; UPDATE=0
NEOFORGE_MAVEN="https://maven.neoforged.net/releases/net/neoforged/neoforge"

die() { echo "error: $*" >&2; exit 1; }
say() { echo "==> $*"; }

while [ $# -gt 0 ]; do
  case "$1" in
    --accept-eula) EULA=1 ;;
    --mc) MC="$2"; shift ;;
    --ram) RAM="$2"; shift ;;
    --whitelist) WHITELIST="$2"; shift ;;
    --motd) MOTD="$2"; shift ;;
    --update) UPDATE=1 ;;
    *) die "unknown option $1" ;;
  esac
  shift
done
[ "$(id -u)" = 0 ] || die "run as root (sudo)"
if [ "$UPDATE" = 1 ]; then
  [ -f $BASE/mc-version ] || die "no server here yet: run the setup first"
  MC=$(cat $BASE/mc-version)
else
  [ "$EULA" = 1 ] || die "the server needs Minecraft's EULA (https://aka.ms/MinecraftEULA) accepted: add --accept-eula"
fi

# ---- packages and user --------------------------------------------------------------------------------------
if [ "$UPDATE" = 0 ]; then
  say "Installing Java and tools"
  export DEBIAN_FRONTEND=noninteractive
  apt-get update -q
  JAVA_PKG=openjdk-21-jre-headless
  case "$MC" in 26.*|2[7-9].*) JAVA_PKG=openjdk-25-jre-headless ;; esac   # 26.x needs Java 25
  apt-get install -y -q "$JAVA_PKG" curl python3 tar
  id minecraft >/dev/null 2>&1 || useradd --system --home-dir $BASE --shell /usr/sbin/nologin minecraft
  mkdir -p $DIR $BASE/backups
fi

# ---- what to install, from the latest GitHub release ----------------------------------------------------------
say "Reading the latest Modern Armory release"
RELEASE_JSON=$(curl -fsSL -H "User-Agent: modern-armory-setup" "https://api.github.com/repos/$REPO/releases/latest")
PLAN=$(MC="$MC" python3 -c '
import json, os, sys, urllib.request
rel = json.loads(sys.stdin.read())
urls = {a["name"]: a["browser_download_url"] for a in rel["assets"]}
req = urllib.request.Request(urls["install.json"], headers={"User-Agent": "modern-armory-setup"})
targets = json.load(urllib.request.urlopen(req))["targets"]
t = next((t for t in targets if t["minecraft"] == os.environ["MC"]), None)
if t is None:
    sys.exit("no release for Minecraft %s (have %s)" % (os.environ["MC"], ", ".join(x["minecraft"] for x in targets)))
print(rel["tag_name"], t["neoforge"])
for m in t["mods"]:
    print(m, urls[m])
' <<< "$RELEASE_JSON")
read -r TAG NEOFORGE <<< "$(head -n1 <<< "$PLAN")"
say "Release $TAG: Minecraft $MC, NeoForge $NEOFORGE"

# ---- NeoForge server -------------------------------------------------------------------------------------------
if [ ! -f "$DIR/.neoforge-$NEOFORGE" ]; then
  say "Installing the NeoForge $NEOFORGE server"
  curl -fsSL -o /tmp/neoforge-installer.jar "$NEOFORGE_MAVEN/$NEOFORGE/neoforge-$NEOFORGE-installer.jar"
  (cd $DIR && java -jar /tmp/neoforge-installer.jar --install-server . > /tmp/neoforge-install.log 2>&1) \
    || { tail -20 /tmp/neoforge-install.log; die "NeoForge's installer failed"; }
  rm -f /tmp/neoforge-installer.jar $DIR/.neoforge-*
  touch "$DIR/.neoforge-$NEOFORGE"
  chmod +x $DIR/run.sh
fi

# ---- the mod and GeckoLib ----------------------------------------------------------------------------------------
[ "$UPDATE" = 1 ] && { say "Stopping the server"; systemctl stop minecraft || true; }
mkdir -p $DIR/mods
say "Installing mods"
find $DIR/mods -maxdepth 1 \( -name 'modernarmory*.jar' -o -name 'geckolib*.jar' \) -delete
tail -n +2 <<< "$PLAN" | while read -r NAME URL; do
  curl -fsSL -o "$DIR/mods/$NAME" "$URL"
  echo "    $NAME"
done

# ---- settings (first install only) -------------------------------------------------------------------------------
if [ "$UPDATE" = 0 ]; then
  echo "$MC" > $BASE/mc-version
  echo "eula=true" > $DIR/eula.txt
  if [ -z "$RAM" ]; then
    TOTAL_GB=$(awk '/MemTotal/ {print int($2 / 1048576)}' /proc/meminfo)
    R=$(( TOTAL_GB - 2 )); [ $R -lt 2 ] && R=2; [ $R -gt 10 ] && R=10
    RAM="${R}G"
  fi
  cat > $DIR/user_jvm_args.txt <<EOF
-Xms$RAM
-Xmx$RAM
-XX:+UseG1GC
-XX:+ParallelRefProcEnabled
-XX:MaxGCPauseMillis=200
-XX:+UnlockExperimentalVMOptions
-XX:+DisableExplicitGC
-XX:G1NewSizePercent=30
-XX:G1MaxNewSizePercent=40
-XX:G1HeapRegionSize=8M
-XX:G1ReservePercent=20
-XX:InitiatingHeapOccupancyPercent=15
EOF
  RCON_PASS=$(head -c 24 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 24)
  touch $DIR/server.properties
  setprop() {  # key value: replace or append in server.properties
    if grep -q "^$1=" $DIR/server.properties; then sed -i "s|^$1=.*|$1=$2|" $DIR/server.properties
    else echo "$1=$2" >> $DIR/server.properties; fi
  }
  setprop online-mode true
  setprop white-list true
  setprop enforce-whitelist true
  setprop motd "$MOTD"
  setprop max-players 20
  setprop view-distance 8
  setprop simulation-distance 6
  setprop difficulty normal
  setprop spawn-protection 0
  setprop enable-rcon true
  setprop rcon.port 25575
  setprop rcon.password "$RCON_PASS"
  setprop broadcast-rcon-to-ops false
  say "Java heap $RAM; online mode and the whitelist on"
fi
chown -R minecraft:minecraft $BASE

# ---- helper commands -------------------------------------------------------------------------------------------
cp "$0" /usr/local/sbin/modern-armory-setup 2>/dev/null || true
chmod 755 /usr/local/sbin/modern-armory-setup 2>/dev/null || true
cat > /usr/local/bin/mc-cmd <<'EOF'
#!/usr/bin/env python3
"""mc-cmd "<console command>": runs it on the local Minecraft server over RCON and prints the reply."""
import socket, struct, sys
props = dict(l.strip().split("=", 1) for l in open("/opt/minecraft/server/server.properties") if "=" in l and not l.startswith("#"))
def pkt(i, t, body):
    data = struct.pack("<ii", i, t) + body.encode("utf-8") + b"\0\0"
    return struct.pack("<i", len(data)) + data
def read(s):
    n = struct.unpack("<i", s.recv(4))[0]
    data = b""
    while len(data) < n:
        data += s.recv(n - len(data))
    i, t = struct.unpack("<ii", data[:8])
    return i, data[8:-2].decode("utf-8", "replace")
s = socket.create_connection(("127.0.0.1", int(props.get("rcon.port", 25575))), timeout=10)
s.sendall(pkt(1, 3, props["rcon.password"]))
if read(s)[0] == -1:
    sys.exit("RCON login failed")
s.sendall(pkt(2, 2, " ".join(sys.argv[1:])))
print(read(s)[1])
EOF
chmod 755 /usr/local/bin/mc-cmd

cat > /usr/local/bin/modern-armory-backup <<'EOF'
#!/usr/bin/env bash
# Daily world backup: saves are paused while the world is copied; the last 7 are kept.
set -u
B=/opt/minecraft/backups; D=/opt/minecraft/server
mc-cmd "save-off" >/dev/null 2>&1; mc-cmd "save-all flush" >/dev/null 2>&1; sleep 5
tar -czf "$B/world-$(date +%F-%H%M).tar.gz" -C "$D" world
mc-cmd "save-on" >/dev/null 2>&1
ls -1t $B/world-*.tar.gz | tail -n +8 | xargs -r rm -f
EOF
chmod 755 /usr/local/bin/modern-armory-backup

# ---- services ----------------------------------------------------------------------------------------------------
cat > /etc/systemd/system/minecraft.service <<EOF
[Unit]
Description=Modern Armory Minecraft server
After=network-online.target
Wants=network-online.target

[Service]
User=minecraft
WorkingDirectory=$DIR
ExecStart=$DIR/run.sh nogui
ExecStop=/usr/local/bin/mc-cmd stop
TimeoutStopSec=120
Restart=on-failure
RestartSec=15
SuccessExitStatus=0 1 130 143

[Install]
WantedBy=multi-user.target
EOF
cat > /etc/systemd/system/modern-armory-backup.service <<EOF
[Unit]
Description=Modern Armory world backup
[Service]
Type=oneshot
User=minecraft
ExecStart=/usr/local/bin/modern-armory-backup
EOF
cat > /etc/systemd/system/modern-armory-backup.timer <<EOF
[Unit]
Description=Daily Modern Armory world backup
[Timer]
OnCalendar=*-*-* 04:00:00
Persistent=true
[Install]
WantedBy=timers.target
EOF
systemctl daemon-reload
systemctl enable --now modern-armory-backup.timer >/dev/null

# ---- firewall: SSH and 25565/tcp only --------------------------------------------------------------------------
if [ "$UPDATE" = 0 ]; then
  if [ -f /etc/iptables/rules.v4 ] && grep -q "REJECT" /etc/iptables/rules.v4; then
    # Oracle Cloud's Ubuntu images ship iptables rules that reject everything but SSH
    say "Opening 25565/tcp in iptables"
    iptables -C INPUT -p tcp --dport 25565 -j ACCEPT 2>/dev/null \
      || iptables -I INPUT "$(iptables -L INPUT --line-numbers | awk '/REJECT/ {print $1; exit}')" -p tcp --dport 25565 -j ACCEPT
    netfilter-persistent save >/dev/null 2>&1 || iptables-save > /etc/iptables/rules.v4
  elif command -v ufw >/dev/null; then
    say "Firewall: SSH and 25565/tcp"
    ufw allow OpenSSH >/dev/null; ufw allow 25565/tcp >/dev/null; ufw --force enable >/dev/null
  fi
fi

# ---- start --------------------------------------------------------------------------------------------------------
say "Starting the server"
systemctl enable minecraft >/dev/null
systemctl restart minecraft
for i in $(seq 1 300); do
  grep -qE 'Done \([0-9.]+s\)!' $DIR/logs/latest.log 2>/dev/null && break
  systemctl is-active --quiet minecraft || { journalctl -u minecraft -n 30 --no-pager; die "the server stopped while starting"; }
  sleep 2
done
grep -qE 'Done \([0-9.]+s\)!' $DIR/logs/latest.log || die "the server didn't finish starting (see journalctl -u minecraft)"
say "Server is up"
if [ -n "$WHITELIST" ]; then
  IFS=',' read -ra NAMES <<< "$WHITELIST"
  for n in "${NAMES[@]}"; do mc-cmd "whitelist add $(echo "$n" | xargs)"; done
fi
IP=$(curl -fsS -4 --max-time 5 https://api.ipify.org 2>/dev/null || hostname -I | awk '{print $1}')
cat <<EOF

Modern Armory $TAG is running (Minecraft $MC, NeoForge $NEOFORGE).
  Address for players:  $IP
  Add a player:         mc-cmd "whitelist add <name>"     Make an admin: mc-cmd "op <name>"
  Logs:                 journalctl -u minecraft -f        Restart: systemctl restart minecraft
  Backups:              $BASE/backups (daily 04:00)       Update the mod: modern-armory-setup --update
EOF
