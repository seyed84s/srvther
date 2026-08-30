package app.srvther.core

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

object XrayConfigGenerator {
    fun generate(vlessUri: String): String {
        val uri = Uri.parse(vlessUri)
        if (uri.scheme != "vless") {
            throw IllegalArgumentException("Only vless:// is supported")
        }

        val uuid = uri.userInfo ?: ""
        val address = uri.host ?: ""
        val port = if (uri.port > 0) uri.port else 443

        val encryption = uri.getQueryParameter("encryption")?.takeIf { it.isNotBlank() } ?: "none"
        val security = uri.getQueryParameter("security")?.takeIf { it.isNotBlank() } ?: "none"
        val type = uri.getQueryParameter("type")?.takeIf { it.isNotBlank() } ?: "tcp"
        val sni = uri.getQueryParameter("sni") ?: ""
        val fp = uri.getQueryParameter("fp")?.takeIf { it.isNotBlank() } ?: "chrome"
        val pbk = uri.getQueryParameter("pbk") ?: ""
        val sid = uri.getQueryParameter("sid") ?: ""
        val spx = uri.getQueryParameter("spx") ?: ""
        val flow = uri.getQueryParameter("flow") ?: ""

        val wsHost = uri.getQueryParameter("host") ?: ""
        val wsPath = uri.getQueryParameter("path")?.takeIf { it.isNotBlank() } ?: "/"

        val grpcServiceName = uri.getQueryParameter("serviceName")
            ?: uri.getQueryParameter("path")
            ?: ""
        val grpcMultiMode = uri.getQueryParameter("mode") == "multi"

        val root = JSONObject()

        // 1. Log
        root.put("log", JSONObject().put("loglevel", "warning"))

        // 2. DNS
        root.put(
            "dns",
            JSONObject().put(
                "servers",
                JSONArray().put("1.1.1.1").put("8.8.8.8"),
            ),
        )

        // 3. Inbounds
        val inbounds = JSONArray()
        val inboundSocks = JSONObject().apply {
            put("port", 10808)
            put("listen", "127.0.0.1")
            put("protocol", "socks")
            put("settings", JSONObject().put("udp", true))
            put("sniffing", JSONObject().apply {
                put("enabled", true)
                put("destOverride", JSONArray().put("http").put("tls").put("quic"))
            })
        }
        inbounds.put(inboundSocks)
        root.put("inbounds", inbounds)

        // 4. Outbounds
        val outbounds = JSONArray()

        // Primary VLESS outbound
        val vlessOutbound = JSONObject().apply {
            put("protocol", "vless")
            put("tag", "proxy")

            val userObj = JSONObject().apply {
                put("id", uuid)
                put("encryption", encryption)
                if (flow.isNotBlank()) {
                    put("flow", flow)
                }
            }

            val vnextObj = JSONObject().apply {
                put("address", address)
                put("port", port)
                put("users", JSONArray().put(userObj))
            }

            put("settings", JSONObject().put("vnext", JSONArray().put(vnextObj)))

            // StreamSettings
            val streamSettings = JSONObject().apply {
                put("network", type)
                put("security", security)

                when (security) {
                    "tls" -> {
                        put(
                            "tlsSettings",
                            JSONObject().apply {
                                if (sni.isNotBlank()) put("serverName", sni)
                                put("alpn", JSONArray().put("h2").put("http/1.1"))
                                put("fingerprint", fp)
                            },
                        )
                    }
                    "reality" -> {
                        put(
                            "realitySettings",
                            JSONObject().apply {
                                if (sni.isNotBlank()) put("serverName", sni)
                                put("publicKey", pbk)
                                put("shortId", sid)
                                put("spiderX", spx)
                                put("fingerprint", fp)
                            },
                        )
                    }
                }

                when (type) {
                    "ws" -> {
                        put(
                            "wsSettings",
                            JSONObject().apply {
                                put("path", wsPath)
                                if (wsHost.isNotBlank()) {
                                    put("headers", JSONObject().put("Host", wsHost))
                                }
                            },
                        )
                    }
                    "grpc" -> {
                        put(
                            "grpcSettings",
                            JSONObject().apply {
                                put("serviceName", grpcServiceName)
                                put("multiMode", grpcMultiMode)
                            },
                        )
                    }
                }
            }
            put("streamSettings", streamSettings)

            // ProxySettings - chain through Aether SOCKS5 on 1819
            put(
                "proxySettings",
                JSONObject().apply {
                    put("tag", "aether-upstream")
                    put("transportLayer", true)
                },
            )
        }
        outbounds.put(vlessOutbound)

        // Upstream SOCKS5 outbound pointing to Srvther (127.0.0.1:1819)
        val aetherOutbound = JSONObject().apply {
            put("protocol", "socks")
            put("tag", "aether-upstream")
            put(
                "settings",
                JSONObject().put(
                    "servers",
                    JSONArray().put(
                        JSONObject().apply {
                            put("address", "127.0.0.1")
                            put("port", 1819)
                        },
                    ),
                ),
            )
        }
        outbounds.put(aetherOutbound)

        root.put("outbounds", outbounds)

        return root.toString(2)
    }
}
