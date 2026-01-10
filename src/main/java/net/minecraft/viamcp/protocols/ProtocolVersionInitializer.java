package net.minecraft.viamcp.protocols;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;

public class ProtocolVersionInitializer {
    
    public static void initializeMissingVersions() {
        // Check and register missing protocol versions if not already present
        try {
            // These might already be present in ViaVersion 5.7.0, but we'll ensure they're available
            if (ProtocolVersion.getProtocol(764) == null) { // 1.20.4
                ProtocolVersion.register(764, "1.20.4");
            }
            
            if (ProtocolVersion.getProtocol(765) == null) { // 1.21.1
                ProtocolVersion.register(765, "1.21.1");
            }
            
            if (ProtocolVersion.getProtocol(766) == null) { // 1.21.3
                ProtocolVersion.register(766, "1.21.3");
            }
            
        } catch (Exception e) {
            System.err.println("Some protocol versions might already be registered: " + e.getMessage());
        }
    }
}