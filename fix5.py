import os, re
f = 'app/src/main/java/app/srvther/ui/HomeScreen.kt'
with open(f, 'r', encoding='utf-8') as file:
    c = file.read()
c = re.sub(r'region = profile.psiphonRegion,', '', c)
c = re.sub(r'selectedRegion = profile.psiphonRegion,', '', c)
c = re.sub(r'psiphonRegion = newRegion,', '', c)
c = re.sub(r'psiphonEnabled = newRegion != app.srvther.model.PsiphonRegion.DIRECT,', '', c)
with open(f, 'w', encoding='utf-8') as file:
    file.write(c)

f = 'app/src/main/java/app/srvther/core/ShareBridge.kt'
with open(f, 'r', encoding='utf-8') as file:
    c = file.read()
c = c.replace('Psiphon', 'VLESS')
with open(f, 'w', encoding='utf-8') as file:
    file.write(c)

f = 'app/src/main/java/app/srvther/core/TunnelConfig.kt'
with open(f, 'r', encoding='utf-8') as file:
    c = file.read()
c = c.replace('Psiphon', 'VLESS')
c = c.replace('PSIPHON_SOCKS_PORT', 'VLESS_SOCKS_PORT')
with open(f, 'w', encoding='utf-8') as file:
    file.write(c)

f = 'app/src/main/java/app/srvther/vpn/SrvtherVpnService.kt'
with open(f, 'r', encoding='utf-8') as file:
    c = file.read()
c = c.replace('psiphonTimeout', '10') # fallback
with open(f, 'w', encoding='utf-8') as file:
    file.write(c)
