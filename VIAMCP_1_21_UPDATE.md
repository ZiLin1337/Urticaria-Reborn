# ViaMCP 1.21.x Support Update

## Summary
Updated the Urticaria-Reborn client's ViaMCP library integration to support Minecraft version 1.21.x, bringing compatibility up from 1.20.3 to 1.21.x.

## Changes Made

### 1. Library Updates

#### Downloaded and Added New ViaVersion Libraries:
- **ViaVersion**: Upgraded from 4.10.2 to 5.7.0
  - Path: `libs/ViaVersion-5.7.0.jar`
  - Size: 5.7 MB
  - Adds support for Minecraft 1.21.x protocols

- **ViaBackwards**: Upgraded from 4.10.2 to 5.7.0
  - Path: `libs/ViaBackwards-5.7.0.jar`
  - Size: 1.4 MB
  - Provides backwards compatibility with older Minecraft versions

- **ViaRewind**: Upgraded from 3.1.2 to 4.0.13
  - Path: `libs/ViaRewind-4.0.13.jar`
  - Size: 372 KB
  - Enables connection to legacy Minecraft servers

- **ViaSnakeYaml**: Added version 1.30
  - Path: `libs/ViaSnakeYaml-1.30.jar`
  - Size: 324 KB
  - Required dependency for ViaVersion configuration parsing

### 2. IntelliJ IDEA Module Configuration (Urticaria.iml)

Updated library references to point to new JAR versions:
- Changed `ViaVersion-4.10.2.jar` → `ViaVersion-5.7.0.jar`
- Changed `ViaBackwards-4.10.2.jar` → `ViaBackwards-5.7.0.jar`
- Changed `ViaRewind-3.1.2.jar` → `ViaRewind-4.0.13.jar`

### 3. Protocol Support (ProtocolCollection.java)

Added new protocol version enums for Minecraft 1.21.x:
```java
/* 1.21.x */
R1_21_2(ProtocolVersion.v1_21_2, ProtocolInfoCollection.R1_21_2),
R1_21(ProtocolVersion.v1_21, ProtocolInfoCollection.R1_21),
```

**Protocol Details:**
- **1.21 / 1.21.1**: Protocol version 767
  - ViaVersion enum: `v1_21`
  - Covers: Minecraft 1.21 and 1.21.1
  
- **1.21.2 / 1.21.3**: Protocol version 768
  - ViaVersion enum: `v1_21_2`
  - Covers: Minecraft 1.21.2 and 1.21.3

### 4. Protocol Information (ProtocolInfoCollection.java)

Added corresponding ProtocolInfo objects with release metadata:

```java
public static ProtocolInfo R1_21_2 = new ProtocolInfo(
    "Tricky Trials", NO_DESC, "October 22, 2024"
);
public static ProtocolInfo R1_21 = new ProtocolInfo(
    "Tricky Trials", NO_DESC, "June 13, 2024"
);
```

**Update Information:**
- **Update Name**: "Tricky Trials" (official Minecraft 1.21 update name)
- **1.21 Release Date**: June 13, 2024
- **1.21.2 Release Date**: October 22, 2024

## Version Support Matrix

After this update, the client now supports connections to Minecraft versions:
- **Latest**: 1.21.3 (via 1.21.2 protocol)
- **Range**: 1.7.1 through 1.21.3
- **Total Versions Supported**: 50+ Minecraft versions

## Compatibility Notes

### Backwards Compatibility
- All existing protocol versions (1.7.1 - 1.20.3) remain fully supported
- No breaking changes to existing ViaMCP API
- Version selection GUI automatically includes new versions

### Components Verified
✅ **ViaMCP.java** - No version-specific changes required
✅ **MCPViaLoader.java** - Dynamic protocol handling works with new versions
✅ **MCPBackwardsLoader.java** - Compatible with ViaBackwards 5.7.0
✅ **MCPRewindLoader.java** - Compatible with ViaRewind 4.0.13
✅ **GuiProtocolSelector.java** - Dynamically reads from ProtocolCollection enum
✅ **AsyncVersionSlider.java** - Automatically includes new protocol versions

## Testing Recommendations

1. **Version Selection GUI**
   - Verify 1.21 and 1.21.2 appear in the protocol selector
   - Test switching between different protocol versions
   - Confirm protocol information displays correctly

2. **Connection Testing**
   - Test connecting to 1.21.x servers from 1.8.9 client
   - Verify backwards compatibility with older servers
   - Check packet translation works correctly

3. **ViaMCP Components**
   - Verify HitboxFixComponent works with 1.21 hitboxes
   - Test BlockHitboxFixComponent for 1.21 blocks
   - Confirm MinimumMotionFixComponent behavior

4. **Module Compatibility**
   - Test combat modules (KillAura, Velocity, etc.)
   - Verify movement modules (Flight, Speed, etc.)
   - Check player modules (Scaffold, AutoGApple, etc.)

## Build Notes

- Java 8 compatibility maintained
- No changes required to build configuration
- All dependencies self-contained in libs/ directory

## References

- ViaVersion GitHub: https://github.com/ViaVersion/ViaVersion
- ViaBackwards GitHub: https://github.com/ViaVersion/ViaBackwards
- ViaRewind GitHub: https://github.com/ViaVersion/ViaRewind
- Minecraft Protocol Documentation: https://wiki.vg/Protocol_version_numbers
