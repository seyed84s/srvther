import os
import re

f = 'app/src/main/java/app/srvther/ui/AdvancedPanel.kt'
with open(f, 'r', encoding='utf-8') as file:
    c = file.read()

c = re.sub(r'import app.srvther.model.PsiphonRegion\n', '', c)
c = re.sub(
    r'// ---------- Psiphon Multi-Country ----------.*?// ---------- Srvther Anti-DPI ----------',
    '''// ---------- VLESS Config ----------
                    SectionHeader("VLESS Configuration")

                    Column(Modifier.fillMaxWidth()) {
                        SettingLabel("VLESS Link")
                        HelperText("Paste your vless:// configuration link here.")
                    }
                    
                    OutlinedTextField(
                        value = profile.vlessConfig,
                        onValueChange = { onProfileChange(profile.copy(vlessConfig = it)) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        label = { Text("vless://...") },
                        minLines = 3,
                        maxLines = 10,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        )
                    )
                    Spacer(Modifier.height(16.dp))
                    
                    // ---------- Srvther Anti-DPI ----------''',
    c, flags=re.DOTALL
)

with open(f, 'w', encoding='utf-8') as file:
    file.write(c)
