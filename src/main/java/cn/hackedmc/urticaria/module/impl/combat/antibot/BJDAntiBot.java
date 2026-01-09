package cn.hackedmc.urticaria.module.impl.combat.antibot;

import cn.hackedmc.urticaria.Client;
import cn.hackedmc.urticaria.module.impl.combat.AntiBot; // 确保导入正确的父类
import cn.hackedmc.urticaria.newevent.Listener;
import cn.hackedmc.urticaria.newevent.annotations.EventLink;
import cn.hackedmc.urticaria.newevent.impl.other.WorldChangeEvent;
import cn.hackedmc.urticaria.newevent.impl.motion.PreUpdateEvent;
import cn.hackedmc.urticaria.newevent.impl.packet.PacketReceiveEvent;
import cn.hackedmc.urticaria.util.chat.ChatUtil;
import cn.hackedmc.urticaria.value.Mode;
import cn.hackedmc.urticaria.value.impl.NumberValue;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S0BPacketAnimation;
import net.minecraft.network.play.server.S0CPacketSpawnPlayer;
import net.minecraft.network.play.server.S13PacketDestroyEntities;
import net.minecraft.network.play.server.S38PacketPlayerListItem;
import net.minecraft.world.WorldSettings;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adapted from obsoverlay, renamed to BJDAntiBot
 */
public class BJDAntiBot extends Mode<AntiBot> { // 继承自 AntiBot

    private final NumberValue respawnTimeValue = new NumberValue("Respawn Time", this, 2500, 0, 10000, 100);

    private final Map<UUID, String> uuidDisplayNames = new ConcurrentHashMap<>();
    private final Map<Integer, String> entityIdDisplayNames = new ConcurrentHashMap<>();
    private final Map<UUID, Long> uuids = new ConcurrentHashMap<>(); // Potentially for Fake Staff detection
    private final Map<UUID, Long> respawnTime = new ConcurrentHashMap<>(); // For BedWars Bot detection

    public BJDAntiBot(String name, AntiBot parent) {
        super(name, parent);
    }

    @Override
    public void onEnable() {
        clearMaps();
    }

    @Override
    public void onDisable() {
        clearMaps();
    }

    private void clearMaps() {
        uuidDisplayNames.clear();
        entityIdDisplayNames.clear();
        uuids.clear();
        respawnTime.clear();
    }

    @EventLink
    public final Listener<WorldChangeEvent> onWorldChange = event -> clearMaps();

    @EventLink
    public final Listener<PreUpdateEvent> onUpdate = event -> {
        // BedWars Bot Check Logic (Check existing entities against respawn timer)
        if (respawnTimeValue.getValue().floatValue() >= 1.0F) {
            for (Entity entity : mc.theWorld.loadedEntityList) {
                if (entity instanceof EntityPlayer && entity != mc.thePlayer) {
                    if (respawnTime.containsKey(entity.getUniqueID())) {
                        long timeDiff = System.currentTimeMillis() - respawnTime.get(entity.getUniqueID());
                        if (timeDiff < respawnTimeValue.getValue().floatValue()) {
                            if (!Client.INSTANCE.getBotManager().contains(entity)) {
                                Client.INSTANCE.getBotManager().add(entity);
                            }
                        } else {
                            // Timer expired, remove from bot list if it was added solely by this check
                            if (Client.INSTANCE.getBotManager().contains(entity) && !entityIdDisplayNames.containsKey(entity.getEntityId())) {
                                Client.INSTANCE.getBotManager().remove(entity);
                            }
                        }
                    }
                }
            }
        }

        // Fake Staff Timeout Logic (from original code)
        for (Map.Entry<UUID, Long> entry :uuids.entrySet()) {
            if (System.currentTimeMillis() - entry.getValue() > 500L) {
                // Original code had a chat message here, commenting out for cleaner bot detection
                // ChatUtil.display("Fake Staff Detected! (" + uuidDisplayNames.get(entry.getKey()) + ")");
                uuids.remove(entry.getKey());
            }
        }
    };

    @EventLink
    public final Listener<PacketReceiveEvent> onPacket = event -> {
        if (mc.theWorld == null) return;
        Packet<?> packet = event.getPacket();

        // Player List Update (for Respawn Timer and Fake Staff/Bot Detection)
        if (packet instanceof S38PacketPlayerListItem) {
            S38PacketPlayerListItem wrapper = (S38PacketPlayerListItem) packet;
            if (wrapper.func_179768_b() == S38PacketPlayerListItem.Action.ADD_PLAYER) {
                for (S38PacketPlayerListItem.AddPlayerData entry : wrapper.func_179767_a()) {
                    UUID id = entry.getProfile().getId();

                    // Logic 1: Record Respawn Time (for BedWars Bot)
                    respawnTime.put(id, System.currentTimeMillis());

                    // Logic 2: Fake Staff/Bot Detection (Survival + No Siblings/Standard Name)
                    // Note: 1.8.9 displayName is IChatComponent, check for null and actual text
                    if (entry.getDisplayName() != null && entry.getDisplayName().getSiblings().isEmpty()) { // Check if name is simple text
                        if (entry.getGameMode() == WorldSettings.GameType.SURVIVAL) {
                            uuids.put(id, System.currentTimeMillis());
                            uuidDisplayNames.put(id, entry.getProfile().getName()); // Store name for potential later use
                        }
                    }
                }
            }
        }
        // Player Animation Packet (Swing)
        else if (packet instanceof S0BPacketAnimation) {
            S0BPacketAnimation wrapper = (S0BPacketAnimation) packet;
            Entity entity = mc.theWorld.getEntityByID(wrapper.getEntityID());
            // If player swings arm, remove from respawn check (likely a real player)
            if (entity != null && wrapper.getAnimationType() == 0 && respawnTime.containsKey(entity.getUniqueID())) {
                respawnTime.remove(entity.getUniqueID());
                // Remove from bot manager if it was added by the respawn timer
                Client.INSTANCE.getBotManager().remove(entity);
            }
        }
        // Player Spawn Packet
        else if (packet instanceof S0CPacketSpawnPlayer) {
            S0CPacketSpawnPlayer wrapper = (S0CPacketSpawnPlayer) packet;
            UUID id = wrapper.getPlayer();

            // Check if this player is a suspected bot (from Player List Update)
            if (uuids.containsKey(id)) {
                String displayName = uuidDisplayNames.get(id);
                // Original code had ChatUtil.display("Bot Detected! (" + displayName + ")"); - commented out for cleaner detection

                // Store entity ID mapping for removal
                entityIdDisplayNames.put(wrapper.getEntityID(), displayName);
                uuids.remove(id); // Remove from suspected list

                // Add to Bot Manager
                // We need to wait for the entity to actually spawn in world to add it object-wise,
                // but we can mark the ID now. The PreUpdateEvent handles actual entity addition.
            }
        }
        // Player Removed Packet
        else if (packet instanceof S13PacketDestroyEntities) {
            S13PacketDestroyEntities wrapper = (S13PacketDestroyEntities) packet;
            for (int entityId : wrapper.getEntityIDs()) {
                if (entityIdDisplayNames.containsKey(entityId)) {
                    // String displayName = entityIdDisplayNames.get(entityId);
                    // Original code had ChatUtil.display("Bot Removed! (" + displayName + ")"); - commented out
                    entityIdDisplayNames.remove(entityId);

                    // Remove from client bot manager
                    Entity entity = mc.theWorld.getEntityByID(entityId);
                    if (entity != null) {
                        Client.INSTANCE.getBotManager().remove(entity);
                    }
                }
            }
        }
    };
}