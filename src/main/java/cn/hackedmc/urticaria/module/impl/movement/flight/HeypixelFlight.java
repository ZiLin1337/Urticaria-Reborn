package cn.hackedmc.urticaria.module.impl.movement.flight;

import cn.hackedmc.urticaria.module.impl.movement.Flight;
import cn.hackedmc.urticaria.newevent.Listener;
import cn.hackedmc.urticaria.newevent.annotations.EventLink;
import cn.hackedmc.urticaria.newevent.impl.motion.PreMotionEvent;
import cn.hackedmc.urticaria.newevent.impl.other.WorldChangeEvent;
import cn.hackedmc.urticaria.newevent.impl.packet.PacketReceiveEvent;
import cn.hackedmc.urticaria.newevent.impl.packet.PacketSendEvent;
import cn.hackedmc.urticaria.util.packet.PacketUtil;
import cn.hackedmc.urticaria.value.Mode;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.protocols.v1_18_2to1_19.packet.ServerboundPackets1_19;
import com.viaversion.viabackwards.protocol.v1_19to1_18_2.Protocol1_19To1_18_2;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

import java.util.LinkedList;
import java.util.Queue;

public class HeypixelFlight extends Mode<Flight> {

    private boolean FuckGrim = false;
    private boolean GrimLLLLLLLLLLLLLLLLLLL = false;
    private int GrimACIsBestAntiCheatLOL = 0;
    public final static Queue<Packet<?>> GrimAC_better_than_polar = new LinkedList<>();

    public HeypixelFlight(String name, Flight parent) {
        super(name, parent);
    }

    @Override
    public void onEnable() {
        GrimACIsBestAntiCheatLOL = 0;
        FuckGrim = false;
        GrimLLLLLLLLLLLLLLLLLLL = false;
    }

    @Override
    public void onDisable() {
        GrimACIsBestAntiCheatLOL = 0;
        FuckGrim = false;
        GrimLLLLLLLLLLLLLLLLLLL = false;
        while (!GrimAC_better_than_polar.isEmpty()) {
            PacketUtil.sendNoEvent(GrimAC_better_than_polar.poll());
        }
    }

    @EventLink
    public final Listener<WorldChangeEvent> onWorldChange = event -> {
        this.getModule(Flight.class).toggle();
        GrimACIsBestAntiCheatLOL = 0;
        FuckGrim = false;
        GrimLLLLLLLLLLLLLLLLLLL = false;
        GrimAC_better_than_polar.clear();
    };

    @EventLink
    public final Listener<PreMotionEvent> onMotion = event -> {
        if (FuckGrim) {
            GrimACIsBestAntiCheatLOL++;
            if (GrimACIsBestAntiCheatLOL >= 8) {
                // Start_Fall_Flying Logic
                // 已恢复代码并修正了 Type 和 协议类
                try {
                    UserConnection conn = Via.getManager().getConnectionManager().getConnections().iterator().next();
                    PacketWrapper wrapper = PacketWrapper.create(ServerboundPackets1_19.PLAYER_COMMAND, conn);

                    wrapper.write(Types.VAR_INT, mc.thePlayer.getEntityId()); // 修正: Type -> Types
                    wrapper.write(Types.VAR_INT, 8); // Start Fall Flying (Action ID 8)
                    wrapper.write(Types.VAR_INT, 0);

                    // 使用你截图中的协议类发送
                    wrapper.sendToServer(Protocol1_19To1_18_2.class);
                } catch (Exception e) {
                    // 如果协议类不匹配，打印错误但不崩端
                    e.printStackTrace();
                }
            }
            GrimACIsBestAntiCheatLOL = 0;
            FuckGrim = false;
        }
    };

    @EventLink
    public final Listener<PacketSendEvent> onPacketSend = event -> {
        Packet<?> packet = event.getPacket();

        if (packet instanceof C0FPacketConfirmTransaction && GrimLLLLLLLLLLLLLLLLLLL) {
            event.setCancelled(true);
            if (GrimAC_better_than_polar.isEmpty()) {
                FuckGrim = true;
            }
            GrimAC_better_than_polar.add(packet);
        }

        if (packet instanceof C02PacketUseEntity) {
            if (GrimLLLLLLLLLLLLLLLLLLL && !GrimAC_better_than_polar.isEmpty()) {
                while (!GrimAC_better_than_polar.isEmpty()) {
                    PacketUtil.sendNoEvent(GrimAC_better_than_polar.poll());
                }
            }
        }
    };

    @EventLink
    public final Listener<PacketReceiveEvent> onPacketReceive = event -> {
        Packet<?> packet = event.getPacket();

        if (packet instanceof S08PacketPlayerPosLook) {
            if (GrimLLLLLLLLLLLLLLLLLLL && !GrimAC_better_than_polar.isEmpty()) {
                while (!GrimAC_better_than_polar.isEmpty()) {
                    PacketUtil.sendNoEvent(GrimAC_better_than_polar.poll());
                }
            }
        }

        if (packet instanceof S12PacketEntityVelocity) {
            S12PacketEntityVelocity v = (S12PacketEntityVelocity) packet;
            if (v.getEntityID() == mc.thePlayer.getEntityId()) {
                if (GrimLLLLLLLLLLLLLLLLLLL || !GrimAC_better_than_polar.isEmpty()) {
                    return;
                }
                GrimACIsBestAntiCheatLOL = 0;
                GrimLLLLLLLLLLLLLLLLLLL = true;
                event.setCancelled(true);
            }
        }
    };
}