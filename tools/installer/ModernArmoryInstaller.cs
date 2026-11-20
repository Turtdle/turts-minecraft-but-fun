// Modern Armory installer: a small Windows exe (C#, .NET Framework 4.x, which ships with Windows 10/11, so players
// need nothing else). Build: tools/installer/build.bat (uses the C# compiler that comes with Windows).
//
// What it does:
//  1. Finds Minecraft Java Edition (%APPDATA%\.minecraft with the launcher's launcher_profiles.json). If it isn't
//     there, it says so and changes nothing.
//  2. Reads install.json from the latest release of the mod's GitHub repository: per Minecraft version, the NeoForge
//     version and the jars to put in the mods folder (the mod itself and GeckoLib). So a new mod version needs a new
//     release, not a new installer.
//  3. Installs NeoForge for that Minecraft version (unless it already is), running NeoForge's own installer with the
//     Java runtime the Minecraft Launcher brings along.
//  4. Puts the mod and GeckoLib into their own game folder (.minecraft\instances\modern-armory-<version>\mods),
//     replacing older copies there and leaving any other mods alone.
//  5. Adds a "Modern Armory (<version>)" profile to the Minecraft Launcher (keeping a backup of the profiles file).
//
// Options (mostly for testing): --version <mc>  --minecraft-dir <dir>  --release-dir <local folder with install.json
// and the jars, instead of GitHub>  --java <java.exe>  --skip-neoforge  --yes (no window: install and exit).

using System;
using System.Collections;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Linq;
using System.Net;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;
using System.Web.Script.Serialization;
using System.Windows.Forms;

[assembly: System.Reflection.AssemblyTitle("Modern Armory Installer")]
[assembly: System.Reflection.AssemblyProduct("Modern Armory")]
[assembly: System.Reflection.AssemblyVersion("1.0.0.0")]

namespace ModernArmory.Installer
{
    /// <summary>What to install for one Minecraft version (one entry of install.json).</summary>
    sealed class Target
    {
        public string Minecraft;      // e.g. "1.21.1"
        public string NeoForge;       // e.g. "21.1.267"
        public List<string> Mods = new List<string>(); // jar file names in the release
        public string Label { get { return "Minecraft " + Minecraft + " (NeoForge " + NeoForge + ")"; } }
        public string VersionId { get { return "neoforge-" + NeoForge; } }
        public string ProfileKey { get { return "modern-armory-" + Minecraft; } }
        public string ProfileName { get { return "Modern Armory (" + Minecraft + ")"; } }
    }

    static class Program
    {
        /// <summary>The GitHub repository the mod is released from ("owner/name").</summary>
        internal const string Repo = "Turtdle/modern-armory";
        internal const string NeoForgeMaven = "https://maven.neoforged.net/releases/net/neoforged/neoforge/";
        /// <summary>Jar name prefixes the installer owns in its mods folder: older copies of these are replaced.</summary>
        internal static readonly string[] OwnedPrefixes = { "modernarmory", "geckolib" };

        [STAThread]
        static int Main(string[] args)
        {
            ServicePointManager.SecurityProtocol = (SecurityProtocolType)3072 | (SecurityProtocolType)12288; // TLS 1.2 + 1.3
            var opts = Options.Parse(args);
            if (opts.Headless)
            {
                var log = new Action<string>(Console.WriteLine);
                try { return new Installer(opts, log).Run(opts.Version) ? 0 : 1; }
                catch (Exception e) { log("Failed: " + e.Message); return 1; }
            }
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.Run(new MainForm(opts));
            return 0;
        }
    }

    sealed class Options
    {
        public string Version, MinecraftDir, ReleaseDir, Java;
        public bool SkipNeoForge, Headless;

        public static Options Parse(string[] a)
        {
            var o = new Options();
            for (int i = 0; i < a.Length; i++)
            {
                switch (a[i])
                {
                    case "--version": o.Version = a[++i]; break;
                    case "--minecraft-dir": o.MinecraftDir = a[++i]; break;
                    case "--release-dir": o.ReleaseDir = a[++i]; break;
                    case "--java": o.Java = a[++i]; break;
                    case "--skip-neoforge": o.SkipNeoForge = true; break;
                    case "--yes": o.Headless = true; break;
                }
            }
            if (o.MinecraftDir == null)
                o.MinecraftDir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), ".minecraft");
            return o;
        }
    }

    sealed class Installer
    {
        readonly Options opts;
        readonly Action<string> log;
        string releaseJsonCache;

        public Installer(Options opts, Action<string> log)
        {
            this.opts = opts;
            this.log = log;
        }

        string ProfilesFile { get { return Path.Combine(opts.MinecraftDir, "launcher_profiles.json"); } }

        /// <summary>Whether Minecraft Java Edition is installed (the launcher has made its folder and profiles file).</summary>
        public bool MinecraftInstalled()
        {
            return Directory.Exists(opts.MinecraftDir) && File.Exists(ProfilesFile);
        }

        /// <summary>A running Minecraft Launcher would overwrite the profile we add.</summary>
        public static bool LauncherRunning()
        {
            foreach (var name in new[] { "MinecraftLauncher", "Minecraft" })
                if (Process.GetProcessesByName(name).Length > 0) return true;
            return false;
        }

        // ---- install.json ---------------------------------------------------------------------------------

        public List<Target> Targets()
        {
            string json = opts.ReleaseDir != null ? File.ReadAllText(Path.Combine(opts.ReleaseDir, "install.json"))
                                                  : DownloadString(AssetUrl("install.json"));
            var root = (Dictionary<string, object>)new JavaScriptSerializer().DeserializeObject(json);
            var list = new List<Target>();
            foreach (var t in (object[])root["targets"])
            {
                var d = (Dictionary<string, object>)t;
                var target = new Target { Minecraft = (string)d["minecraft"], NeoForge = (string)d["neoforge"] };
                foreach (var m in (object[])d["mods"]) target.Mods.Add((string)m);
                list.Add(target);
            }
            return list;
        }

        string LatestRelease()
        {
            if (releaseJsonCache == null)
                releaseJsonCache = DownloadString("https://api.github.com/repos/" + Program.Repo + "/releases/latest");
            return releaseJsonCache;
        }

        string AssetUrl(string fileName)
        {
            var release = (Dictionary<string, object>)new JavaScriptSerializer().DeserializeObject(LatestRelease());
            foreach (var a in (object[])release["assets"])
            {
                var asset = (Dictionary<string, object>)a;
                if ((string)asset["name"] == fileName) return (string)asset["browser_download_url"];
            }
            throw new Exception(fileName + " is missing from the latest Modern Armory release");
        }

        // ---- the install ----------------------------------------------------------------------------------

        public bool Run(string minecraftVersion)
        {
            if (!MinecraftInstalled())
            {
                log("Minecraft Java Edition isn't installed here (" + opts.MinecraftDir + "). Nothing was changed.");
                return false;
            }
            var targets = Targets();
            var target = minecraftVersion == null ? targets[0] : targets.FirstOrDefault(t => t.Minecraft == minecraftVersion);
            if (target == null) throw new Exception("No Modern Armory release for Minecraft " + minecraftVersion);
            log("Installing Modern Armory for " + target.Label);

            if (!opts.SkipNeoForge) InstallNeoForge(target);

            string gameDir = Path.Combine(opts.MinecraftDir, "instances", target.ProfileKey);
            string mods = Path.Combine(gameDir, "mods");
            Directory.CreateDirectory(mods);
            foreach (var old in Directory.GetFiles(mods, "*.jar"))
            {
                string name = Path.GetFileName(old).ToLowerInvariant();
                if (Program.OwnedPrefixes.Any(p => name.StartsWith(p)) && !target.Mods.Contains(Path.GetFileName(old)))
                {
                    File.Delete(old);
                    log("Removed old " + Path.GetFileName(old));
                }
            }
            foreach (var jar in target.Mods)
            {
                string dest = Path.Combine(mods, jar);
                if (opts.ReleaseDir != null) File.Copy(Path.Combine(opts.ReleaseDir, jar), dest, true);
                else DownloadFile(AssetUrl(jar), dest);
                log("Added " + jar);
            }

            AddProfile(target, gameDir);
            log("Done. Open the Minecraft Launcher, choose \"" + target.ProfileName + "\" next to the Play button, and press Play.");
            return true;
        }

        void InstallNeoForge(Target target)
        {
            string versionJson = Path.Combine(opts.MinecraftDir, "versions", target.VersionId, target.VersionId + ".json");
            if (File.Exists(versionJson))
            {
                log("NeoForge " + target.NeoForge + " is already installed.");
                return;
            }
            string java = opts.Java ?? FindJava();
            if (java == null)
                throw new Exception("Couldn't find Java. Start Minecraft once from the Minecraft Launcher (it downloads its own Java), then run this again.");
            string jar = Path.Combine(Path.GetTempPath(), "neoforge-" + target.NeoForge + "-installer.jar");
            log("Downloading NeoForge " + target.NeoForge + "...");
            DownloadFile(Program.NeoForgeMaven + target.NeoForge + "/neoforge-" + target.NeoForge + "-installer.jar", jar);
            log("Installing NeoForge " + target.NeoForge + " (this takes a minute)...");
            var psi = new ProcessStartInfo(java, "-jar \"" + jar + "\" --install-client \"" + opts.MinecraftDir + "\"")
            {
                UseShellExecute = false, CreateNoWindow = true, RedirectStandardOutput = true, RedirectStandardError = true,
                WorkingDirectory = Path.GetTempPath()
            };
            using (var p = Process.Start(psi))
            {
                p.OutputDataReceived += (s, e) => { if (e.Data != null && e.Data.Trim().Length > 0) log("  " + e.Data); };
                p.ErrorDataReceived += (s, e) => { if (e.Data != null && e.Data.Trim().Length > 0) log("  " + e.Data); };
                p.BeginOutputReadLine();
                p.BeginErrorReadLine();
                p.WaitForExit();
                if (p.ExitCode != 0 || !File.Exists(versionJson))
                    throw new Exception("NeoForge's installer failed (exit code " + p.ExitCode + ").");
            }
            try { File.Delete(jar); } catch (IOException) { }
            log("NeoForge " + target.NeoForge + " installed.");
        }

        /// <summary>
        /// A Java to run NeoForge's installer with: the runtimes the Minecraft Launcher downloads (Microsoft Store and
        /// classic launcher), then JAVA_HOME, then the PATH. The newest one found wins.
        /// </summary>
        public static string FindJava()
        {
            var roots = new List<string>
            {
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                    @"Packages\Microsoft.4297127D64EC6_8wekyb3d8bbwe\LocalCache\Local\runtime"),
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), @".minecraft\runtime"),
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86), @"Minecraft Launcher\runtime"),
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), @"Minecraft Launcher\runtime"),
            };
            var found = new List<string>();
            foreach (var root in roots.Where(Directory.Exists))
            {
                try { found.AddRange(Directory.GetFiles(root, "java.exe", SearchOption.AllDirectories)); }
                catch (Exception) { }
            }
            string home = Environment.GetEnvironmentVariable("JAVA_HOME");
            if (home != null && File.Exists(Path.Combine(home, @"bin\java.exe"))) found.Add(Path.Combine(home, @"bin\java.exe"));
            if (found.Count == 0) found.Add("java"); // the PATH, if any
            string best = null;
            int bestMajor = 0;
            foreach (var j in found)
            {
                int major = JavaMajor(j);
                if (major > bestMajor) { best = j; bestMajor = major; }
            }
            return bestMajor >= 21 ? best : null;
        }

        static int JavaMajor(string java)
        {
            try
            {
                var psi = new ProcessStartInfo(java, "-version")
                { UseShellExecute = false, CreateNoWindow = true, RedirectStandardError = true, RedirectStandardOutput = true };
                using (var p = Process.Start(psi))
                {
                    string text = p.StandardError.ReadToEnd() + p.StandardOutput.ReadToEnd();
                    p.WaitForExit(15000);
                    var m = Regex.Match(text, "version \"(\\d+)(\\.(\\d+))?");
                    if (!m.Success) return 0;
                    int major = int.Parse(m.Groups[1].Value);
                    return major == 1 && m.Groups[3].Success ? int.Parse(m.Groups[3].Value) : major;
                }
            }
            catch (Exception) { return 0; }
        }

        /// <summary>Adds (or updates) the launcher profile, keeping everything else in the file as it was.</summary>
        void AddProfile(Target target, string gameDir)
        {
            File.Copy(ProfilesFile, ProfilesFile + ".bak-modern-armory", true);
            var js = new JavaScriptSerializer { MaxJsonLength = int.MaxValue };
            var root = (Dictionary<string, object>)js.DeserializeObject(File.ReadAllText(ProfilesFile));
            if (!root.ContainsKey("profiles")) root["profiles"] = new Dictionary<string, object>();
            var profiles = (Dictionary<string, object>)root["profiles"];
            string now = DateTime.UtcNow.ToString("yyyy-MM-ddTHH:mm:ss.fffZ");
            Dictionary<string, object> p;
            if (profiles.ContainsKey(target.ProfileKey)) p = (Dictionary<string, object>)profiles[target.ProfileKey];
            else profiles[target.ProfileKey] = p = new Dictionary<string, object> { { "created", now } };
            p["name"] = target.ProfileName;
            p["type"] = "custom";
            p["lastVersionId"] = target.VersionId;
            p["gameDir"] = gameDir;
            p["icon"] = "Furnace";
            p["lastUsed"] = now;
            if (!p.ContainsKey("javaArgs")) p["javaArgs"] = "-Xmx4G -XX:+UnlockExperimentalVMOptions -XX:+UseG1GC";
            File.WriteAllText(ProfilesFile, js.Serialize(root), new UTF8Encoding(false));
            log("Added the \"" + target.ProfileName + "\" profile to the Minecraft Launcher.");
        }

        // ---- downloads ------------------------------------------------------------------------------------

        static WebClient Client()
        {
            var c = new WebClient();
            c.Headers[HttpRequestHeader.UserAgent] = "ModernArmoryInstaller/1.0";
            return c;
        }

        static string DownloadString(string url)
        {
            using (var c = Client()) return c.DownloadString(url);
        }

        static void DownloadFile(string url, string dest)
        {
            string tmp = dest + ".part";
            using (var c = Client()) c.DownloadFile(url, tmp);
            if (File.Exists(dest)) File.Delete(dest);
            File.Move(tmp, dest);
        }
    }

    /// <summary>The window: pick a Minecraft version, press Install, watch the log.</summary>
    sealed class MainForm : Form
    {
        readonly Options opts;
        readonly ComboBox versions = new ComboBox { DropDownStyle = ComboBoxStyle.DropDownList, Width = 300 };
        readonly Button install = new Button { Text = "Install", Width = 110, Height = 30 };
        readonly TextBox output = new TextBox { Multiline = true, ReadOnly = true, ScrollBars = ScrollBars.Vertical, Dock = DockStyle.Fill };
        List<Target> targets;

        public MainForm(Options opts)
        {
            this.opts = opts;
            Text = "Modern Armory Installer";
            Width = 620;
            Height = 420;
            StartPosition = FormStartPosition.CenterScreen;
            Font = new Font("Segoe UI", 9.5f);
            var top = new FlowLayoutPanel { Dock = DockStyle.Top, Height = 48, Padding = new Padding(10, 10, 10, 0) };
            top.Controls.Add(new Label { Text = "Minecraft version:", AutoSize = true, Padding = new Padding(0, 6, 4, 0) });
            top.Controls.Add(versions);
            top.Controls.Add(install);
            var body = new Panel { Dock = DockStyle.Fill, Padding = new Padding(10) };
            body.Controls.Add(output);
            Controls.Add(body);
            Controls.Add(top);
            install.Enabled = false;
            install.Click += (s, e) => StartInstall();
            Shown += (s, e) => Load();
        }

        void Log(string line)
        {
            if (InvokeRequired) { BeginInvoke(new Action<string>(Log), line); return; }
            output.AppendText(line + Environment.NewLine);
        }

        new void Load()
        {
            var probe = new Installer(opts, Log);
            if (!probe.MinecraftInstalled())
            {
                MessageBox.Show(this, "Minecraft Java Edition isn't installed on this PC (or hasn't been opened yet).\n\nNothing was changed.",
                    "Modern Armory", MessageBoxButtons.OK, MessageBoxIcon.Information);
                Close();
                return;
            }
            Log("Found Minecraft in " + opts.MinecraftDir);
            Log("Getting the latest Modern Armory release...");
            new Thread(() =>
            {
                try
                {
                    var t = probe.Targets();
                    BeginInvoke(new Action(() =>
                    {
                        targets = t;
                        foreach (var x in t) versions.Items.Add(x.Label);
                        versions.SelectedIndex = 0;
                        install.Enabled = true;
                        Log("Pick a version and press Install.");
                    }));
                }
                catch (Exception e) { Log("Couldn't reach the release: " + e.Message); }
            }) { IsBackground = true }.Start();
        }

        void StartInstall()
        {
            if (Installer.LauncherRunning())
            {
                MessageBox.Show(this, "Please close the Minecraft Launcher first, then press Install again.",
                    "Modern Armory", MessageBoxButtons.OK, MessageBoxIcon.Warning);
                return;
            }
            var target = targets[versions.SelectedIndex];
            install.Enabled = false;
            versions.Enabled = false;
            new Thread(() =>
            {
                try
                {
                    new Installer(opts, Log).Run(target.Minecraft);
                    BeginInvoke(new Action(() => MessageBox.Show(this,
                        "Modern Armory is installed.\n\nOpen the Minecraft Launcher, pick \"" + target.ProfileName + "\" and press Play.",
                        "Modern Armory", MessageBoxButtons.OK, MessageBoxIcon.Information)));
                }
                catch (Exception e)
                {
                    Log("Failed: " + e.Message);
                    BeginInvoke(new Action(() => { install.Enabled = true; versions.Enabled = true; }));
                }
            }) { IsBackground = true }.Start();
        }
    }
}
