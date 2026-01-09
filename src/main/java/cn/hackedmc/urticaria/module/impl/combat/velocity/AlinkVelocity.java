package cn.hackedmc.urticaria.module.impl.combat.velocity;

import cn.hackedmc.urticaria.Client;
import cn.hackedmc.urticaria.component.impl.player.RotationComponent;
import cn.hackedmc.urticaria.module.impl.combat.KillAura;
import cn.hackedmc.urticaria.module.impl.combat.Velocity;
import cn.hackedmc.urticaria.newevent.Listener;
import cn.hackedmc.urticaria.newevent.Priorities;
import cn.hackedmc.urticaria.newevent.annotations.EventLink;
import cn.hackedmc.urticaria.newevent.impl.motion.PreUpdateEvent;
import cn.hackedmc.urticaria.newevent.impl.packet.PacketReceiveEvent;
import cn.hackedmc.urticaria.newevent.impl.packet.PacketSendEvent;
import cn.hackedmc.urticaria.util.packet.PacketUtil;
import cn.hackedmc.urticaria.util.RayCastUtil;
import cn.hackedmc.urticaria.value.Mode;
import cn.hackedmc.urticaria.value.impl.BooleanValue;
import cn.hackedmc.urticaria.value.impl.BoundsNumberValue;
import cn.hackedmc.urticaria.value.impl.NumberValue;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C00PacketKeepAlive;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.util.MovingObjectPosition;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;

public class AlinkVelocity extends Mode<Velocity> {

    private final BoundsNumberValue targetRange = new BoundsNumberValue("Target Range", this, 2.5, 5.0, 0.0, 10.0, 0.1);
    private final NumberValue maxDelay = new NumberValue("Max Delay", this, 20, 0, 100, 1);
    private final BoundsNumberValue attackCount = new BoundsNumberValue("Attack Count", this, 3, 3, 0, 20, 1);
    private final BooleanValue debug = new BooleanValue("Debug", this, false);
    private final BooleanValue requireKillAura = new BooleanValue("Require KillAura", this, true);

    private Entity target;
    private int attackQueue;
    private boolean receiveDamage;
    private int delayTicks = -1;
    private final Queue<Packet<?>> packets = new ConcurrentLinkedQueue<>();

    public AlinkVelocity(String name, Velocity parent) {
        super(name, parent);
    }

    @Override
    public void onDisable() {
        target = null;
        attackQueue = 0;
        receiveDamage = false;
        delayTicks = -1;
        while (!packets.isEmpty()) {
            PacketUtil.sendNoEvent(packets.poll());
        }
    }

    @EventLink(value = Priorities.HIGH)
    public final Listener<PacketReceiveEvent> onPacketReceive = event -> {
        if (mc.thePlayer == null || mc.theWorld == null) return;
        Packet<?> packet = event.getPacket();

        if (packet instanceof S12PacketEntityVelocity) {
            S12PacketEntityVelocity wrapper = (S12PacketEntityVelocity) packet;
            if (wrapper.getEntityID() == mc.thePlayer.getEntityId()) {
                receiveDamage = true;
            }
        } else if (packet instanceof S19PacketEntityStatus) {
            S19PacketEntityStatus wrapper = (S19PacketEntityStatus) packet;
            if (wrapper.getOpCode() == 2 && wrapper.getEntity(mc.theWorld) == mc.thePlayer && receiveDamage) {
                receiveDamage = false;

                if (mc.thePlayer.capabilities.isCreativeMode) return;

                if (!findTarget()) return;

                // Alink logic: Delay packets
                if (target == null) {
                    if (debug.getValue()) {
                        // ChatUtil.display("Alink: Starting delay sequence...");
                    }
                    delayTicks = maxDelay.getValue().intValue();
                } else {
                    attackQueue = ThreadLocalRandom.current().nextInt(attackCount.getValue().intValue(), attackCount.getSecondValue().intValue() + 1);
                }
            }
        }
    };

    @EventLink(value = Priorities.HIGH)
    public final Listener<PacketSendEvent> onPacketSend = event -> {
        if (mc.thePlayer == null) return;
        Packet<?> packet = event.getPacket();

        if (delayTicks >= 0) {
            if (packet instanceof C0FPacketConfirmTransaction || packet instanceof C00PacketKeepAlive) {
                event.setCancelled(true);
                packets.add(packet);
            } else if (packet instanceof C02PacketUseEntity || packet instanceof C0APacketAnimation) {
                handlePackets();
            }
        }
    };

    @EventLink
    public final Listener<PreUpdateEvent> onUpdate = event -> {
        if (delayTicks > 0) {
            if (mc.thePlayer.isSpectator()) {
                delayTicks = 0;
            } else if (!findTarget()) {
                delayTicks--;
            } else if (target != null) {
                delayTicks = 0;
            } else {
                delayTicks--;
            }
        } else if (delayTicks == 0) {
            handlePackets();
            if (target != null) {
                attackQueue = ThreadLocalRandom.current().nextInt(attackCount.getValue().intValue(), attackCount.getSecondValue().intValue() + 1);
            }
        } else if (attackQueue > 0 && delayTicks == -1) {
            if (target == null) {
                attackQueue = 0;
            } else {
                while (attackQueue > 0) {
                    PacketUtil.send(new C02PacketUseEntity(target, C02PacketUseEntity.Action.ATTACK));
                    mc.thePlayer.swingItem();
                    attackQueue--;
                }
                target = null;
            }
        }
    };

    private boolean findTarget() {
        target = null;

        if (KillAura.INSTANCE.isEnabled() && KillAura.INSTANCE.target != null) {
            Entity kaTarget = KillAura.INSTANCE.target;
            if (mc.thePlayer.getDistanceToEntity(kaTarget) > targetRange.getValue().floatValue()) {
                target = kaTarget;
                return true;
            }
            target = kaTarget;
            return true;
        }

        MovingObjectPosition rayTrace = RayCastUtil.rayCast(RotationComponent.rotations, targetRange.getSecondValue().doubleValue());
        if (rayTrace != null && rayTrace.entityHit != null && rayTrace.entityHit instanceof EntityLivingBase) {
            target = rayTrace.entityHit;
            return true;
        }

        if (requireKillAura.getValue() && !KillAura.INSTANCE.isEnabled()) {
            return false;
        }

        double minDist = Double.MAX_VALUE;
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (entity instanceof EntityLivingBase && entity != mc.thePlayer && !entity.isDead) {
                double dist = mc.thePlayer.getDistanceToEntity(entity);
                if (dist <= targetRange.getSecondValue().doubleValue() && dist < minDist) {
                    minDist = dist;
                    target = entity;
                }
            }
        }

        return target != null;
    }

    private void handlePackets() {
        while (!packets.isEmpty()) {
            PacketUtil.sendNoEvent(packets.poll());
        }
        delayTicks = -1;
    }
}