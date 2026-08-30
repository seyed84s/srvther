import os, re
f = 'app/src/main/java/app/srvther/ui/HomeScreen.kt'
with open(f, 'r', encoding='utf-8') as file:
    c = file.read()
# Remove CountrySelectorButton entirely
c = re.sub(r'if \(profile\.psiphonEnabled\)\s*\{.*?\}\n', '', c, flags=re.DOTALL)
c = re.sub(r'CountrySelectorButton\(.*?\}\)', '', c, flags=re.DOTALL)
c = re.sub(r'CountrySelectorSheet\(.*?\}\)', '', c, flags=re.DOTALL)
with open(f, 'w', encoding='utf-8') as file:
    file.write(c)

f = 'app/src/main/java/app/srvther/vpn/SrvtherVpnService.kt'
with open(f, 'r', encoding='utf-8') as file:
    c = file.read()
c = c.replace('isChainedPsiphon', 'isChainedVless')
c = c.replace('isPsiphon', 'isVless')
c = c.replace('psiphonEngine', 'xrayController')
c = c.replace('psiphonAlive', 'xrayAlive')
c = c.replace('Psiphon failed to restart', 'Xray failed to restart')
c = c.replace('PsiphonRegion', 'Any')
c = c.replace('val xrayAlive = if (isChainedVless) xrayController?.isAlive() == true else true', 'val xrayAlive = true')
c = c.replace('val stillPsiAlive = if (isChainedVless) xrayController?.isAlive() == true else true', 'val stillPsiAlive = true')

c = re.sub(
    r'// Restart Psiphon if chained.*?\}',
    '''// Restart Xray if chained
            if (isChainedVless) {
                app.srvther.core.CoreNativeManager.initCoreEnv(this)
                val xrayJson = app.srvther.core.XrayConfigGenerator.generate(profile.vlessConfig)
                val handler = object : libv2ray.CoreCallbackHandler {
                    override fun onStart() {}
                    override fun onStop() {}
                }
                xrayController = app.srvther.core.CoreNativeManager.newCoreController(handler)
                xrayController?.startLoop(xrayJson)
                if (!app.srvther.core.PortProbe.awaitOpen("127.0.0.1", 10808, 10000)) {
                    DiagnosticsLog.w(TAG, "Xray failed to restart - retrying.")
                    continue
                }
            }''', c, flags=re.DOTALL)

with open(f, 'w', encoding='utf-8') as file:
    file.write(c)
