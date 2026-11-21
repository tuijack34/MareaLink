using System.Collections.Concurrent;
using System.Net;
using System.Net.Sockets;
using System.Net.WebSockets;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;

namespace MareaPC;

/// <summary>
/// Servidor WebSocket mínimo en la red local (sin dependencias).
/// El teléfono se conecta a ws://IP:47820/ws?token=... (o ?code=1234 la primera vez).
/// </summary>
sealed class Server
{
    public const int Port = 47820;
    public const int DiscoveryPort = 47821;

    readonly MediaBridge _media;
    readonly Config _cfg;
    readonly ConcurrentDictionary<WebSocket, string> _clients = new();
    readonly SemaphoreSlim _sendLock = new(1, 1);

    public event Action? ClientsChanged;
    public event Action? PairingRequested;
    public IReadOnlyCollection<string> ClientNames => _clients.Values.ToList();

    public Server(MediaBridge media, Config cfg)
    {
        _media = media;
        _cfg = cfg;
        _media.Changed += () => _ = Broadcast(StateJson());
        _media.CoverChanged += () => _ = Broadcast(CoverJson());
    }

    public void Start()
    {
        _ = Task.Run(AcceptLoop);
        _ = Task.Run(DiscoveryLoop);
        _ = Task.Run(async () =>
        {
            // La posición avanza sola: mandamos el estado cada segundo mientras suena
            while (true)
            {
                await Task.Delay(1000);
                if (_media.Playing && !_clients.IsEmpty) await Broadcast(StateJson());
            }
        });
    }

    async Task AcceptLoop()
    {
        var listener = new TcpListener(IPAddress.Any, Port);
        try { listener.Start(); }
        catch (Exception ex) { Log.Add($"No pude abrir el puerto {Port}: {ex.Message}"); return; }
        Log.Add($"Escuchando en el puerto {Port}");
        while (true)
        {
            var tcp = await listener.AcceptTcpClientAsync();
            _ = Task.Run(() => Handle(tcp));
        }
    }

    async Task Handle(TcpClient tcp)
    {
        tcp.NoDelay = true;
        var stream = tcp.GetStream();
        try
        {
            var req = await ReadHeaders(stream);
            if (req == null) { tcp.Close(); return; }
            var (path, query, headers) = req.Value;

            if (path == "/info")
            {
                await WriteText(stream, 200, JsonSerializer.Serialize(new { app = "MareaPC", name = Environment.MachineName, version = 1 }), "application/json");
                tcp.Close();
                return;
            }
            if (path != "/ws" || !headers.TryGetValue("sec-websocket-key", out var key))
            {
                await WriteText(stream, 404, "not found");
                tcp.Close();
                return;
            }

            query.TryGetValue("device", out var device);
            device = string.IsNullOrWhiteSpace(device) ? "Teléfono" : device;
            string? newToken = null;
            var token = query.GetValueOrDefault("token");
            if (token == null || !_cfg.Tokens.Contains(token))
            {
                var code = query.GetValueOrDefault("code");
                if (code != null && code == _cfg.Code)
                {
                    newToken = Convert.ToHexString(RandomNumberGenerator.GetBytes(16));
                    _cfg.Tokens.Add(newToken);
                    _cfg.Save();
                    Log.Add($"Emparejado: {device}");
                }
                else
                {
                    Log.Add($"{device} pidió conectarse ({(code == null ? "sin código" : "código incorrecto")})");
                    PairingRequested?.Invoke();
                    await WriteText(stream, 401, code == null ? "pairing required" : "wrong code");
                    tcp.Close();
                    return;
                }
            }

            var accept = Convert.ToBase64String(SHA1.HashData(Encoding.ASCII.GetBytes(key.Trim() + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11")));
            var resp = "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
                       $"Sec-WebSocket-Accept: {accept}\r\n\r\n";
            var rb = Encoding.ASCII.GetBytes(resp);
            await stream.WriteAsync(rb);

            var ws = WebSocket.CreateFromStream(stream, isServer: true, subProtocol: null, keepAliveInterval: TimeSpan.FromSeconds(20));
            _clients[ws] = device;
            ClientsChanged?.Invoke();
            Log.Add($"Conectado: {device}");

            await Send(ws, JsonSerializer.Serialize(new { type = "hello", name = Environment.MachineName, token = newToken }));
            await Send(ws, StateJson());
            await Send(ws, CoverJson());

            var buf = new byte[16 * 1024];
            while (ws.State == WebSocketState.Open)
            {
                var ms = new MemoryStream();
                WebSocketReceiveResult r;
                do
                {
                    r = await ws.ReceiveAsync(buf, CancellationToken.None);
                    if (r.MessageType == WebSocketMessageType.Close) break;
                    ms.Write(buf, 0, r.Count);
                } while (!r.EndOfMessage);
                if (r.MessageType == WebSocketMessageType.Close) break;
                await OnMessage(Encoding.UTF8.GetString(ms.ToArray()));
            }
        }
        catch (Exception ex) when (ex is IOException or WebSocketException or ObjectDisposedException) { }
        catch (Exception ex) { Log.Add("Error de conexión: " + ex.Message); }
        finally
        {
            foreach (var kv in _clients.Where(kv => kv.Key.State != WebSocketState.Open).ToList())
            {
                _clients.TryRemove(kv.Key, out _);
                Log.Add($"Desconectado: {kv.Value}");
            }
            ClientsChanged?.Invoke();
            try { tcp.Close(); } catch { }
        }
    }

    async Task OnMessage(string text)
    {
        try
        {
            using var doc = JsonDocument.Parse(text);
            var root = doc.RootElement;
            var cmd = root.GetProperty("cmd").GetString() ?? "";
            long? ms = root.TryGetProperty("ms", out var m) && m.ValueKind == JsonValueKind.Number ? m.GetInt64() : null;
            string? arg = root.TryGetProperty("arg", out var a) ? a.GetString() : null;
            await _media.Exec(cmd, ms, arg);
        }
        catch (Exception ex) { Log.Add("Mensaje inválido: " + ex.Message); }
    }

    string StateJson()
    {
        var (pos, dur) = _media.Timeline();
        return JsonSerializer.Serialize(new
        {
            type = "state",
            app = _media.App,
            hasSession = _media.HasSession,
            title = _media.Title,
            artist = _media.Artist,
            album = _media.Album,
            playing = _media.Playing,
            positionMs = pos,
            durationMs = dur,
            shuffle = _media.Shuffle,
            repeat = _media.Repeat,
            canNext = _media.CanNext,
            canPrevious = _media.CanPrevious,
            canSeek = _media.CanSeek,
            coverKey = _media.CoverKey,
        });
    }

    string CoverJson() => JsonSerializer.Serialize(new
    {
        type = "cover",
        key = _media.CoverKey,
        data = _media.Cover != null ? Convert.ToBase64String(_media.Cover) : null,
    });

    async Task Broadcast(string json)
    {
        foreach (var ws in _clients.Keys.ToList())
            if (ws.State == WebSocketState.Open) await Send(ws, json);
    }

    async Task Send(WebSocket ws, string json)
    {
        await _sendLock.WaitAsync();
        try { await ws.SendAsync(Encoding.UTF8.GetBytes(json), WebSocketMessageType.Text, true, CancellationToken.None); }
        catch { }
        finally { _sendLock.Release(); }
    }

    // ---------- HTTP mínimo ----------

    static async Task<(string path, Dictionary<string, string> query, Dictionary<string, string> headers)?> ReadHeaders(NetworkStream s)
    {
        var sb = new StringBuilder();
        var one = new byte[1];
        while (sb.Length < 16 * 1024)
        {
            var n = await s.ReadAsync(one);
            if (n == 0) return null;
            sb.Append((char)one[0]);
            if (sb.Length >= 4 && sb[^1] == '\n' && sb[^2] == '\r' && sb[^3] == '\n' && sb[^4] == '\r') break;
        }
        var lines = sb.ToString().Split("\r\n", StringSplitOptions.RemoveEmptyEntries);
        if (lines.Length == 0) return null;
        var parts = lines[0].Split(' ');
        if (parts.Length < 2) return null;
        var target = parts[1];
        var qi = target.IndexOf('?');
        var path = qi >= 0 ? target[..qi] : target;
        var query = new Dictionary<string, string>();
        if (qi >= 0)
            foreach (var kv in target[(qi + 1)..].Split('&', StringSplitOptions.RemoveEmptyEntries))
            {
                var eq = kv.IndexOf('=');
                if (eq > 0) query[Uri.UnescapeDataString(kv[..eq])] = Uri.UnescapeDataString(kv[(eq + 1)..].Replace('+', ' '));
            }
        var headers = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
        foreach (var l in lines.Skip(1))
        {
            var c = l.IndexOf(':');
            if (c > 0) headers[l[..c].Trim()] = l[(c + 1)..].Trim();
        }
        return (path, query, headers);
    }

    static async Task WriteText(NetworkStream s, int code, string body, string type = "text/plain")
    {
        var b = Encoding.UTF8.GetBytes(body);
        var head = $"HTTP/1.1 {code} {(code == 200 ? "OK" : "Error")}\r\nContent-Type: {type}; charset=utf-8\r\nContent-Length: {b.Length}\r\nConnection: close\r\n\r\n";
        await s.WriteAsync(Encoding.ASCII.GetBytes(head));
        await s.WriteAsync(b);
    }

    // ---------- Descubrimiento ----------

    /// El teléfono manda "MAREA_DISCOVER" por broadcast UDP y la PC contesta con su nombre.
    async Task DiscoveryLoop()
    {
        UdpClient udp;
        try { udp = new UdpClient(new IPEndPoint(IPAddress.Any, DiscoveryPort)) { EnableBroadcast = true }; }
        catch (Exception ex) { Log.Add($"No pude abrir el descubrimiento (UDP {DiscoveryPort}): {ex.Message}"); return; }
        while (true)
        {
            try
            {
                var r = await udp.ReceiveAsync();
                if (Encoding.UTF8.GetString(r.Buffer).Trim() != "MAREA_DISCOVER") continue;
                var reply = Encoding.UTF8.GetBytes(JsonSerializer.Serialize(new { app = "MareaPC", name = Environment.MachineName, port = Port }));
                await udp.SendAsync(reply, r.RemoteEndPoint);
            }
            catch (Exception ex) { Log.Add("Descubrimiento: " + ex.Message); await Task.Delay(1000); }
        }
    }
}
