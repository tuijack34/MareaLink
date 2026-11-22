using System.Text.Json;
using Microsoft.Win32;

namespace MareaPC;

static class Program
{
    [STAThread]
    static void Main()
    {
        using var single = new Mutex(true, "MareaPC-single-instance", out var first);
        if (!first) return; // ya está abierto

        Application.EnableVisualStyles();
        Application.SetCompatibleTextRenderingDefault(false);
        Application.SetHighDpiMode(HighDpiMode.PerMonitorV2);

        var cfg = Config.Load();
        var media = new MediaBridge();
        var server = new Server(media, cfg);
        Task.Run(async () =>
        {
            try { await media.StartAsync(); }
            catch (Exception ex) { Log.Add("No pude leer los reproductores de Windows: " + ex.Message); }
        });
        server.Start();
        Application.Run(new Tray(cfg, media, server));
    }
}

/// <summary>Configuración en %APPDATA%\MareaPC\config.json</summary>
sealed class Config
{
    public string Code { get; set; } = Random.Shared.Next(1000, 10000).ToString();
    public List<string> Tokens { get; set; } = new();
    /// Solo visible y conectable mientras TIDAL de escritorio está abierto
    public bool OnlyWithTidal { get; set; } = true;
    public bool FirstRunDone { get; set; }

    static string Dir => Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "MareaPC");
    static string FilePath => Path.Combine(Dir, "config.json");

    public static Config Load()
    {
        try
        {
            if (File.Exists(FilePath))
                return JsonSerializer.Deserialize<Config>(File.ReadAllText(FilePath)) ?? new Config();
        }
        catch { }
        var c = new Config();
        c.Save();
        return c;
    }

    public void Save()
    {
        try
        {
            Directory.CreateDirectory(Dir);
            File.WriteAllText(FilePath, JsonSerializer.Serialize(this, new JsonSerializerOptions { WriteIndented = true }));
        }
        catch { }
    }
}

static class Log
{
    static readonly List<string> _lines = new();
    public static event Action? Added;
    public static void Add(string s)
    {
        lock (_lines)
        {
            _lines.Add($"{DateTime.Now:HH:mm:ss} {s}");
            if (_lines.Count > 300) _lines.RemoveAt(0);
        }
        Added?.Invoke();
    }
    public static string All() { lock (_lines) return string.Join(Environment.NewLine, _lines); }
}

/// <summary>Ícono en la bandeja del sistema con el código para emparejar.</summary>
sealed class Tray : ApplicationContext
{
    readonly NotifyIcon _icon;
    readonly Config _cfg;
    readonly Server _server;
    readonly MediaBridge _media;
    readonly ToolStripMenuItem _status = new() { Enabled = false };
    readonly ToolStripMenuItem _playing = new() { Enabled = false };
    readonly ToolStripMenuItem _code = new() { Enabled = false };
    readonly ToolStripMenuItem _startup = new("Iniciar con Windows") { CheckOnClick = true };
    readonly ToolStripMenuItem _onlyTidal = new("Solo cuando TIDAL está abierto") { CheckOnClick = true };
    readonly System.Windows.Forms.Timer _watch = new() { Interval = 3000 };
    bool? _tidalOpen;
    DateTime _forceVisibleUntil = DateTime.MinValue;
    readonly SynchronizationContext _ui;

    const string RunKey = @"Software\Microsoft\Windows\CurrentVersion\Run";

    public Tray(Config cfg, MediaBridge media, Server server)
    {
        _cfg = cfg; _media = media; _server = server;
        _ui = SynchronizationContext.Current ?? new WindowsFormsSynchronizationContext();

        var menu = new ContextMenuStrip();
        menu.Items.Add(new ToolStripMenuItem("Marea PC") { Enabled = false, Font = new Font(SystemFonts.MenuFont ?? SystemFonts.DefaultFont, FontStyle.Bold) });
        menu.Items.Add(_status);
        menu.Items.Add(_playing);
        menu.Items.Add(new ToolStripSeparator());
        menu.Items.Add(_code);
        menu.Items.Add("Nuevo código", null, (_, _) => { _cfg.Code = Random.Shared.Next(1000, 10000).ToString(); _cfg.Save(); Refresh(); ShowCode(); });
        menu.Items.Add("Olvidar teléfonos emparejados", null, (_, _) => { _cfg.Tokens.Clear(); _cfg.Save(); Log.Add("Se olvidaron los teléfonos emparejados"); });
        menu.Items.Add(new ToolStripSeparator());
        if (!_cfg.FirstRunDone)
        {
            // la primera vez queda arrancando con Windows: si no, no se puede abrir solo con TIDAL
            SetStartup(true);
            _cfg.FirstRunDone = true;
            _cfg.Save();
        }
        _startup.Checked = IsStartup();
        _startup.CheckedChanged += (_, _) => SetStartup(_startup.Checked);
        menu.Items.Add(_startup);
        _onlyTidal.Checked = _cfg.OnlyWithTidal;
        _onlyTidal.CheckedChanged += (_, _) => { _cfg.OnlyWithTidal = _onlyTidal.Checked; _cfg.Save(); _tidalOpen = null; Watch(); };
        menu.Items.Add(_onlyTidal);
        menu.Items.Add("Ver registro", null, (_, _) => ShowLog());
        menu.Items.Add("Salir", null, (_, _) => { _icon!.Visible = false; Application.Exit(); Environment.Exit(0); });

        using var s = typeof(Tray).Assembly.GetManifestResourceStream("MareaPC.marea.ico");
        _icon = new NotifyIcon
        {
            Icon = s != null ? new Icon(s) : SystemIcons.Application,
            Text = "Marea PC",
            ContextMenuStrip = menu,
            Visible = true,
        };
        _icon.DoubleClick += (_, _) => ShowCode();

        _server.ClientsChanged += () => _ui.Post(_ => Refresh(), null);
        _server.PairingRequested += () => _ui.Post(_ => ShowCode(), null);
        _media.Changed += () => _ui.Post(_ => Refresh(), null);
        Refresh();
        if (_cfg.OnlyWithTidal && !TidalRunning())
        {
            // abierto a mano con TIDAL cerrado: avisamos y nos escondemos hasta que se abra TIDAL
            _forceVisibleUntil = DateTime.Now.AddSeconds(8);
            ShowBalloon("Marea PC está listo", "Va a aparecer solo cuando abras TIDAL (y se esconde cuando lo cerrás).");
        }
        else ShowBalloon("Marea PC está corriendo", $"Para conectar Marea Link usá el código {_cfg.Code}. Lo ves con clic derecho en este ícono.");
        _watch.Tick += (_, _) => Watch();
        _watch.Start();
        Watch();
    }

    static bool TidalRunning()
    {
        try { return System.Diagnostics.Process.GetProcessesByName("TIDAL").Length > 0; }
        catch { return true; }
    }

    /// Con "Solo cuando TIDAL está abierto": aparece y acepta conexiones solo mientras TIDAL corre.
    void Watch()
    {
        var open = !_cfg.OnlyWithTidal || TidalRunning();
        _icon.Visible = open || DateTime.Now < _forceVisibleUntil;
        if (open == _tidalOpen) return;
        _tidalOpen = open;
        _server.SetActive(open);
        Log.Add(open ? "TIDAL abierto: Marea PC activo" : "TIDAL cerrado: Marea PC en espera");
    }

    void Refresh()
    {
        var n = _server.ClientNames.Count;
        _status.Text = n == 0 ? "Sin teléfonos conectados" : $"Conectado: {string.Join(", ", _server.ClientNames)}";
        _playing.Text = _media.HasSession
            ? Trunc($"{(_media.Playing ? "▶" : "⏸")} {_media.Title} — {_media.Artist}", 60)
            : "No hay nada sonando (abrí TIDAL)";
        _code.Text = $"Código para emparejar: {_cfg.Code}";
        _icon.Text = Trunc(n == 0 ? "Marea PC" : $"Marea PC · {n} conectado(s)", 63);
    }

    void ShowCode() => ShowBalloon("Emparejar Marea Link", $"Código: {_cfg.Code}");
    void ShowBalloon(string title, string text) { _icon.BalloonTipTitle = title; _icon.BalloonTipText = text; _icon.ShowBalloonTip(5000); }

    static string Trunc(string s, int max) => s.Length <= max ? s : s[..(max - 1)] + "…";

    static bool IsStartup()
    {
        using var k = Registry.CurrentUser.OpenSubKey(RunKey);
        return k?.GetValue("MareaPC") != null;
    }

    static void SetStartup(bool on)
    {
        using var k = Registry.CurrentUser.OpenSubKey(RunKey, writable: true) ?? Registry.CurrentUser.CreateSubKey(RunKey);
        if (on) k.SetValue("MareaPC", $"\"{Environment.ProcessPath}\"");
        else k.DeleteValue("MareaPC", throwOnMissingValue: false);
    }

    void ShowLog()
    {
        var f = new Form { Text = "Marea PC — registro", Width = 640, Height = 420, StartPosition = FormStartPosition.CenterScreen };
        var box = new TextBox { Multiline = true, ReadOnly = true, ScrollBars = ScrollBars.Vertical, Dock = DockStyle.Fill, Font = new Font(FontFamily.GenericMonospace, 9), Text = Log.All() };
        f.Controls.Add(box);
        f.Show();
    }
}
