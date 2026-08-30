package app.srvther.core

import android.net.Uri

object XrayConfigGenerator {
    fun generate(vlessUri: String): String {
        val uri = Uri.parse(vlessUri)
        if (uri.scheme != "vless") {
            throw IllegalArgumentException("Only vless:// is supported")
        }
        
        val uuid = uri.userInfo
        val address = uri.host
        val port = uri.port
        
        val encryption = uri.getQueryParameter("encryption") ?: "none"
        val security = uri.getQueryParameter("security") ?: "none"
        val type = uri.getQueryParameter("type") ?: "tcp"
        val sni = uri.getQueryParameter("sni") ?: ""
        val fp = uri.getQueryParameter("fp") ?: ""
        val pbk = uri.getQueryParameter("pbk") ?: ""
        val sid = uri.getQueryParameter("sid") ?: ""
        val flow = uri.getQueryParameter("flow") ?: ""
        
        val wsHost = uri.getQueryParameter("host") ?: ""
        val wsPath = uri.getQueryParameter("path") ?: "/"
        
        val grpcAuthority = uri.getQueryParameter("authority") ?: ""
        val grpcServiceName = uri.getQueryParameter("serviceName") ?: ""
        val grpcMultiMode = uri.getQueryParameter("mode") == "multi"
        
        return """
        {
          "log": { "loglevel": "warning" },
          "inbounds": [
            {
              "port": 10808,
              "listen": "127.0.0.1",
              "protocol": "socks",
              "settings": { "udp": true }
            }
          ],
          "outbounds": [
            {
              "protocol": "vless",
              "settings": {
                "vnext": [
                  {
                    "address": "$address",
                    "port": $port,
                    "users": [
                      {
                        "id": "$uuid",
                        "encryption": "$encryption",
                        "flow": "$flow"
                      }
                    ]
                  }
                ]
              },
              "streamSettings": {
                "network": "$type",
                "security": "$security",
                "tlsSettings": {
                  "serverName": "$sni",
                  "alpn": ["h2", "http/1.1"],
                  "fingerprint": "$fp"
                },
                "realitySettings": {
                  "serverName": "$sni",
                  "publicKey": "$pbk",
                  "shortId": "$sid",
                  "spiderX": "",
                  "fingerprint": "$fp"
                },
                "wsSettings": {
                  "path": "$wsPath",
                  "headers": {
                    "Host": "$wsHost"
                  }
                },
                "grpcSettings": {
                  "authority": "$grpcAuthority",
                  "serviceName": "$grpcServiceName",
                  "multiMode": $grpcMultiMode
                }
              },
              "proxySettings": {
                "tag": "aether"
              }
            },
            {
              "protocol": "socks",
              "tag": "aether",
              "settings": {
                "servers": [
                  { "address": "127.0.0.1", "port": 1819 }
                ]
              }
            }
          ]
        }
        """.trimIndent()
    }
}
