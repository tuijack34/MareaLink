using Windows.Media.Control;
using Windows.Storage.Streams;

namespace MareaPC;

/// <summary>
/// Lee y controla el reproductor de Windows (los mismos controles que usan las teclas multimedia).
/// Prefiere la sesión de TIDAL; si TIDAL no está abierto, usa la que Windows considere actual.
/// </summary>
sealed class MediaBridge
{
    GlobalSystemMediaTransportControlsSessionManager? _mgr;
    GlobalSystemMediaTransportControlsSession? _cur;
    readonly SemaphoreSlim _refreshLock = new(1, 1);

    public event Action? Changed;
    public event Action? CoverChanged;

    // Último estado conocido
    public string App { get; private set; } = "";
    public string Title { get; private set; } = "";
    public string Artist { get; private set; } = "";
    public string Album { get; private set; } = "";
    public bool Playing { get; private set; }
    public bool? Shuffle { get; private set; }
    public string Repeat { get; private set; } = "off";
    public bool CanNext { get; private set; }
    public bool CanPrevious { get; private set; }
    public bool CanSeek { get; private set; }
    public bool HasSession => _cur != null;
    public string CoverKey { get; private set; } = "";
    public byte[]? Cover { get; private set; }

    TimeSpan _pos, _end;
    DateTimeOffset _posAt;

    public async Task StartAsync()
    {
        _mgr = await GlobalSystemMediaTransportControlsSessionManager.RequestAsync();
        _mgr.SessionsChanged += (_, _) => Pick();
        _mgr.CurrentSessionChanged += (_, _) => Pick();
        Pick();
    }

    void Pick()
    {
        if (_mgr == null) return;
        var sessions = _mgr.GetSessions();
        var s = sessions.FirstOrDefault(x => x.SourceAppUserModelId.Contains("tidal", StringComparison.OrdinalIgnoreCase))
                ?? _mgr.GetCurrentSession();
        if (!ReferenceEquals(s, _cur))
        {
            if (_cur != null)
            {
                _cur.MediaPropertiesChanged -= OnMedia;
                _cur.PlaybackInfoChanged -= OnPlayback;
                _cur.TimelinePropertiesChanged -= OnTimeline;
            }
            _cur = s;
            if (_cur != null)
            {
                _cur.MediaPropertiesChanged += OnMedia;
                _cur.PlaybackInfoChanged += OnPlayback;
                _cur.TimelinePropertiesChanged += OnTimeline;
            }
            Log.Add($"Reproductor: {(_cur?.SourceAppUserModelId ?? "ninguno")}");
        }
        _ = Refresh();
    }

    void OnMedia(GlobalSystemMediaTransportControlsSession s, MediaPropertiesChangedEventArgs e) => _ = Refresh();
    void OnPlayback(GlobalSystemMediaTransportControlsSession s, PlaybackInfoChangedEventArgs e) => _ = Refresh();
    void OnTimeline(GlobalSystemMediaTransportControlsSession s, TimelinePropertiesChangedEventArgs e) => _ = Refresh();

    async Task Refresh()
    {
        await _refreshLock.WaitAsync();
        try
        {
            var s = _cur;
            if (s == null)
            {
                App = ""; Title = ""; Artist = ""; Album = ""; Playing = false;
                Changed?.Invoke();
                return;
            }
            App = s.SourceAppUserModelId;
            var pi = s.GetPlaybackInfo();
            Playing = pi.PlaybackStatus == GlobalSystemMediaTransportControlsSessionPlaybackStatus.Playing;
            Shuffle = pi.IsShuffleActive;
            Repeat = pi.AutoRepeatMode switch
            {
                Windows.Media.MediaPlaybackAutoRepeatMode.Track => "one",
                Windows.Media.MediaPlaybackAutoRepeatMode.List => "all",
                _ => "off",
            };
            CanNext = pi.Controls.IsNextEnabled;
            CanPrevious = pi.Controls.IsPreviousEnabled;
            CanSeek = pi.Controls.IsPlaybackPositionEnabled;

            var tl = s.GetTimelineProperties();
            _pos = tl.Position;
            _end = tl.EndTime;
            _posAt = tl.LastUpdatedTime;

            try
            {
                var p = await s.TryGetMediaPropertiesAsync();
                if (p != null)
                {
                    Title = p.Title ?? "";
                    Artist = p.Artist ?? "";
                    Album = p.AlbumTitle ?? "";
                    var key = $"{Title}|{Artist}|{Album}";
                    if (key != CoverKey)
                    {
                        CoverKey = key;
                        Cover = p.Thumbnail != null ? await ReadAll(p.Thumbnail) : null;
                        CoverChanged?.Invoke();
                    }
                }
            }
            catch (Exception ex) { Log.Add("No pude leer el tema: " + ex.Message); }

            Changed?.Invoke();
        }
        finally { _refreshLock.Release(); }
    }

    static async Task<byte[]?> ReadAll(IRandomAccessStreamReference r)
    {
        try
        {
            using var stream = await r.OpenReadAsync();
            if (stream.Size == 0 || stream.Size > 4_000_000) return null;
            using var reader = new DataReader(stream.GetInputStreamAt(0));
            await reader.LoadAsync((uint)stream.Size);
            var bytes = new byte[stream.Size];
            reader.ReadBytes(bytes);
            return bytes;
        }
        catch { return null; }
    }

    /// <summary>Posición estimada ahora (Windows solo avisa cada tanto).</summary>
    public (long posMs, long durMs) Timeline()
    {
        var pos = _pos;
        if (Playing && _posAt != default)
        {
            var since = DateTimeOffset.Now - _posAt;
            if (since > TimeSpan.Zero && since < TimeSpan.FromHours(3)) pos += since;
        }
        if (_end > TimeSpan.Zero && pos > _end) pos = _end;
        if (pos < TimeSpan.Zero) pos = TimeSpan.Zero;
        return ((long)pos.TotalMilliseconds, (long)_end.TotalMilliseconds);
    }

    public async Task<bool> Exec(string cmd, long? ms = null, string? arg = null)
    {
        var s = _cur;
        try
        {
            switch (cmd)
            {
                case "open":
                    // Experimental: pedirle a TIDAL de escritorio que abra un tema por link.
                    if (!string.IsNullOrEmpty(arg) && arg.All(char.IsAsciiLetterOrDigit))
                    {
                        System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo($"tidal://track/{arg}") { UseShellExecute = true });
                        Log.Add($"Abrí tidal://track/{arg}");
                        return true;
                    }
                    return false;
            }
            if (s == null) return false;
            var ok = cmd switch
            {
                "play" => await s.TryPlayAsync(),
                "pause" => await s.TryPauseAsync(),
                "toggle" => await s.TryTogglePlayPauseAsync(),
                "next" => await s.TrySkipNextAsync(),
                "previous" => await s.TrySkipPreviousAsync(),
                "seek" when ms != null => await s.TryChangePlaybackPositionAsync(TimeSpan.FromMilliseconds(ms.Value).Ticks),
                "shuffle" => await s.TryChangeShuffleActiveAsync(!(Shuffle ?? false)),
                "repeat" => await s.TryChangeAutoRepeatModeAsync(Repeat switch
                {
                    "off" => Windows.Media.MediaPlaybackAutoRepeatMode.List,
                    "all" => Windows.Media.MediaPlaybackAutoRepeatMode.Track,
                    _ => Windows.Media.MediaPlaybackAutoRepeatMode.None,
                }),
                _ => false,
            };
            if (cmd == "seek" && ms != null) { _pos = TimeSpan.FromMilliseconds(ms.Value); _posAt = DateTimeOffset.Now; }
            _ = Refresh();
            return ok;
        }
        catch (Exception ex)
        {
            Log.Add($"Falló \"{cmd}\": {ex.Message}");
            return false;
        }
    }
}
