# Srvther

![Srvther](Docs/Srvther.png)

### اینترنت آزاد برای همه:))
**[راهنمای فارسی](README.fa.md)** · **[English Guide](Docs/DOCS.en.md)** · **[راهنمای کامل فارسی](Docs/DOCS.fa.md)**

Telegram: https://t.me/CluvexStudio

Srvther is a censorship circumvention client designed for heavily restricted networks. It automatically discovers reachable routes, establishes an encrypted tunnel, and exposes a local SOCKS5 proxy for your applications.

Unlike traditional VPN clients, Srvther is built for environments where Deep Packet Inspection (DPI), protocol fingerprinting, UDP throttling, and endpoint blocking are common.

## Features

- Automatic endpoint discovery, with end-to-end data-plane validation so a gateway is only trusted once it actually passes traffic, not just once it answers the handshake
- MASQUE (HTTP/3 & HTTP/2), with optional TLS ClientHello fragmentation on HTTP/2
- WireGuard support
- Nested WireGuard mode (`gool`)
- Traffic obfuscation
- Routing rules by domain, address, or port, matched from the TLS server name so they keep working behind a tun front end
- Upstream proxy support, so Srvther can dial out through another VPN or proxy already running on the machine
- Automatic reconnection, and quick-reconnect to your last known-good gateway to skip rescanning
- Local SOCKS5 proxy
- Command-line flags, environment variables, or interactive prompts — your choice
- Linux, Windows, macOS and Android (Termux)

## Download

Prebuilt binaries are available on the Releases page for:

- Linux
- Windows
- macOS
- Android (Termux)

### Termux (Android) — one-line install

```bash
curl -fsSL https://raw.githubusercontent.com/CluvexStudio/srvther/main/srvther.sh -o srvther.sh && chmod +x srvther.sh && ./srvther.sh install
```

This detects your device architecture, downloads the matching release, verifies its checksum, and installs `srvther` into `$PREFIX/bin`. Run it afterwards with:

```bash
srvther
```

To update later, run `./srvther.sh update`. To remove it, run `./srvther.sh uninstall`.

## Build

### Requirements

- Rust (latest stable)
- C/C++ compiler
- CMake

The `quiche` repository must be placed alongside `srvther`:

```text
<repo>/
  srvther/
  quiche/
```

Build:

```bash
cargo build --release
```

Binary:

```text
target/release/srvther
```

## Docker

You can run Srvther in an isolated environment using Docker. The official image is available on GitHub Container Registry (GHCR).

> **The SOCKS5 proxy has no authentication.** Anyone who can reach the port can use your tunnel. Every command below publishes the port to `127.0.0.1` only, so it stays reachable from your own machine and nothing else. Do not replace it with `-p 1819:1819`, because that form listens on every interface of the host and turns the proxy into an open relay. If you genuinely need to serve other machines, put an authenticated front end in front of it and firewall the port.

The `-v srvther-data:/data` volume keeps the generated WARP identity between runs. Without it every start registers a brand new device, and Cloudflare begins rate limiting your address.

Pull and run the pre-built image (interactive mode is required for initial setup):

```bash
docker run -it -p 127.0.0.1:1819:1819 -v srvther-data:/data ghcr.io/cluvexstudio/srvther:latest
```

You can also bypass prompts by providing environment variables:

```bash
docker run -it -p 127.0.0.1:1819:1819 -v srvther-data:/data \
  -e AETHER_PROTOCOL=masque \
  -e AETHER_SCAN=balanced \
  ghcr.io/cluvexstudio/srvther:latest
```

If you prefer to build the image manually from source:

```bash
docker build -t srvther .
docker run -it -p 127.0.0.1:1819:1819 -v srvther-data:/data srvther
```

## Usage

Run with no arguments and answer the prompts:

```bash
./target/release/srvther
```

Or skip the prompts with flags:

```bash
./target/release/srvther --masque -4 --scan turbo --noize firewall
```

On Windows, double-click `run-srvther.bat` (included in the release zip) instead — it opens a terminal, runs `srvther.exe`, and keeps the window open afterwards so you can read any errors.

Every prompt has a flag and an environment variable equivalent. Run `./target/release/srvther --help` for the full list, or see the guides linked below.

After startup, a SOCKS5 proxy will be available at:

```
127.0.0.1:1819
```

Example:

```bash
curl -x socks5h://127.0.0.1:1819 https://www.cloudflare.com/cdn-cgi/trace
```

## Supported Protocols

### MASQUE (Recommended)

Encapsulates traffic over HTTP/3 (QUIC) or HTTP/2 (TLS), making it resemble ordinary HTTPS traffic.

### WireGuard

Fast and lightweight transport for networks with less aggressive inspection.

### Nested WireGuard (`gool`)

A WireGuard tunnel running inside another WireGuard tunnel, providing an additional encryption layer.

## Documentation

Detailed documentation is available in:

- [Docs/GUIDE.en.md](Docs/GUIDE.en.md) — English guide
- [Docs/GUIDE.fa.md](Docs/GUIDE.fa.md) — راهنمای فارسی

## Credits

Developed by **CluvexStudio**. :))

MASQUE support is built on top of Cloudflare's **Quiche** library.


## Contributing

> **Experienced network developers and protocol engineers are welcome to contribute.**

> **Please keep the codebase clean, maintainable, and well-engineered. Low-quality or vibe-coded contributions will not be accepted.**

## Donate

If Srvther has been useful to you, consider supporting its development:

- **TRX (Tron):** `TRxVSHcoADZnBfztFmFb2TQopusAwWYEVR`
- **BTC:** `bc1qnjnvzsa5avgj7n0uy383cv5zdxfjnvvp257egm`
- **TON:** `UQAH75bXaaRUhZMwiF0ZujOXFDDmvLSPASKoOsWF0HNasiaM`

## License

See the LICENSE file for licensing information.
