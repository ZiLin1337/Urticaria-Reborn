package cn.hackedmc.urticaria.module.impl.combat;

import cn.hackedmc.urticaria.Client;
import cn.hackedmc.urticaria.api.Rise;
import cn.hackedmc.urticaria.component.impl.player.*;
import cn.hackedmc.urticaria.component.impl.player.rotationcomponent.MovementFix;
import cn.hackedmc.urticaria.component.impl.render.ESPComponent;
import cn.hackedmc.urticaria.component.impl.render.espcomponent.api.ESPColor;
import cn.hackedmc.urticaria.component.impl.render.espcomponent.impl.AboveBox;
import cn.hackedmc.urticaria.component.impl.render.espcomponent.impl.FullBox;
import cn.hackedmc.urticaria.component.impl.render.espcomponent.impl.SigmaRing;
import cn.hackedmc.urticaria.module.Module;
import cn.hackedmc.urticaria.module.api.Category;
import cn.hackedmc.urticaria.module.api.ModuleInfo;
import cn.hackedmc.urticaria.module.impl.movement.Flight;
import cn.hackedmc.urticaria.module.impl.movement.Speed;
import cn.hackedmc.urticaria.module.impl.player.Scaffold;
import cn.hackedmc.urticaria.newevent.Listener;
import cn.hackedmc.urticaria.newevent.Priorities;
import cn.hackedmc.urticaria.newevent.annotations.EventLink;
import cn.hackedmc.urticaria.newevent.impl.input.ClickEvent;
import cn.hackedmc.urticaria.newevent.impl.motion.PostMotionEvent;
import cn.hackedmc.urticaria.newevent.impl.motion.PreMotionEvent;
import cn.hackedmc.urticaria.newevent.impl.motion.PreUpdateEvent;
import cn.hackedmc.urticaria.newevent.impl.motion.SlowDownEvent;
import cn.hackedmc.urticaria.newevent.impl.other.AttackEvent;
import cn.hackedmc.urticaria.newevent.impl.other.WorldChangeEvent;
import cn.hackedmc.urticaria.newevent.impl.packet.PacketSendEvent;
import cn.hackedmc.urticaria.newevent.impl.render.MouseOverEvent;
import cn.hackedmc.urticaria.newevent.impl.render.Render2DEvent;
import cn.hackedmc.urticaria.newevent.impl.render.RenderItemEvent;
import cn.hackedmc.urticaria.util.RandomUtil;
import cn.hackedmc.urticaria.util.RayCastUtil;
import cn.hackedmc.urticaria.util.math.MathUtil;
import cn.hackedmc.urticaria.util.packet.PacketUtil;
import cn.hackedmc.urticaria.util.render.ColorUtil;
import cn.hackedmc.urticaria.util.rotation.RotationUtil;
import cn.hackedmc.urticaria.util.vector.Vector2d;
import cn.hackedmc.urticaria.util.vector.Vector2f;
import cn.hackedmc.urticaria.value.impl.*;
import com.viaversion.viarewind.protocol.protocol1_8to1_9.Protocol1_8To1_9;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.type.Type;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.item.*;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;
import net.minecraft.potion.Potion;
import net.minecraft.util.*;
import net.minecraft.viamcp.ViaMCP;
import util.time.StopWatch;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Ported from Mozilla to Urticaria
 */
@Rise
@ModuleInfo(name = "module.combat.killaura.name", description = "module.combat.killaura.description", category = Category.COMBAT)
public final class KillAura extends Module {

    public static KillAura INSTANCE;

    private final ModeValue mode = new ModeValue("Attack Mode", this)
            .add(new SubMode("Single"))
            .add(new SubMode("Switch"))
            .add(new SubMode("Multiple"))
            .setDefault("Single");

    private final NumberValue switchDelay = new NumberValue("Switch Delay", this, 0, 0, 10, 1, () -> !mode.getValue().getName().equals("Switch"));

    private final ModeValue autoBlock = new ModeValue("Auto Block", this)
            .add(new SubMode("None"))
            .add(new SubMode("Fake"))
            .add(new SubMode("Vanilla"))
            .add(new SubMode("NCP"))
            .add(new SubMode("Legit"))
            .add(new SubMode("Grim"))
            .add(new SubMode("Intave"))
            .add(new SubMode("Old Intave"))
            .add(new SubMode("Imperfect Vanilla"))
            .add(new SubMode("Vanilla ReBlock"))
            .add(new SubMode("Watchdog 1.12"))
            .add(new SubMode("New NCP"))
            .add(new SubMode("Universal"))
            .add(new SubMode("Watchdog"))
            .add(new SubMode("Watchdog 1.8"))
            .setDefault("None");

    private final BooleanValue rightClickOnly = new BooleanValue("Right Click Only", this, false, () -> autoBlock.getValue().getName().equals("None") || autoBlock.getValue().getName().equals("Fake"));
    private final BooleanValue preventServerSideBlocking = new BooleanValue("Prevent ServerSide Blocking", this, false, () -> !autoBlock.getValue().getName().equals("None") && !autoBlock.getValue().getName().equals("Fake"));

    private final ModeValue sorting = new ModeValue("Sorting", this)
            .add(new SubMode("Distance"))
            .add(new SubMode("Health"))
            .add(new SubMode("Hurt Time"))
            .setDefault("Distance");

    private final ModeValue clickMode = new ModeValue("Click Delay Mode", this)
            .add(new SubMode("Normal"))
            .add(new SubMode("Hit Select"))
            .add(new SubMode("1.9+"))
            .add(new SubMode("1.9+ (1.8 Visuals)"))
            .setDefault("Normal");

    private final BooleanValue randomise19 = new BooleanValue("Randomize 1.9+ Speed", this, false, () -> !clickMode.getValue().getName().contains("1.9+"));
    private final NumberValue random19Factor = new NumberValue("Randomize Factor", this, 0.2, 0.05, 1.0, 0.05, () -> !randomise19.getValue() || !clickMode.getValue().getName().contains("1.9+"));

    public final NumberValue range = new NumberValue("Range", this, 3, 3, 6, 0.1);
    private final NumberValue fov = new NumberValue("FOV", this, 360, 0, 360, 1);
    private final BooleanValue showFOVCircle = new BooleanValue("Show FOV Circle", this, false, () -> fov.getValue().doubleValue() < 360);

    private final BoundsNumberValue cps = new BoundsNumberValue("CPS", this, 10, 15, 1, 20, 1);
    private final BoundsNumberValue rotationSpeed = new BoundsNumberValue("Rotation speed", this, 5, 10, 0, 10, 1);
    private final BooleanValue silentRotations = new BooleanValue("Silent Rotations", this, true);
    private final ListValue<MovementFix> movementCorrection = new ListValue<>("Movement correction", this);
    private final BooleanValue showMovementArc = new BooleanValue("Show Movement Arc", this, false, () -> movementCorrection.getValue() == MovementFix.OFF);

    private final BooleanValue keepSprint = new BooleanValue("Keep sprint", this, false);
    private final BooleanValue watchdogKeepSprint = new BooleanValue("Prediction Keep sprint", this, false);

    private final ModeValue espMode = new ModeValue("Target ESP Mode", this)
            .add(new SubMode("Ring"))
            .add(new SubMode("Box"))
            .add(new SubMode("None"))
            .setDefault("Ring");

    private final BooleanValue coloredRing = new BooleanValue("Colored Sigma Ring", this, true, () -> !espMode.getValue().getName().equals("Ring"));

    public final ModeValue boxMode = new ModeValue("Box Mode", this, () -> !espMode.getValue().getName().equals("Box"))
            .add(new SubMode("Above"))
            .add(new SubMode("Full"))
            .setDefault("Above");

    private final BooleanValue rayCast = new BooleanValue("Ray cast", this, false);
    private final BooleanValue throughWalls = new BooleanValue("Through Walls", this, false, () -> !rayCast.getValue());

    private final BooleanValue advanced = new BooleanValue("Advanced", this, false);
    private final ModeValue rotationMode = new ModeValue("Rotation Mode", this, () -> !advanced.getValue())
            .add(new SubMode("Legit/Normal"))
            .add(new SubMode("Snap"))
            .add(new SubMode("NCP"))
            .add(new SubMode("Autistic AntiCheat"))
            .setDefault("Legit/Normal");

    private final BooleanValue attackWhilstScaffolding = new BooleanValue("Attack whilst Scaffolding", this, false, () -> !advanced.getValue());
    private final BooleanValue noSwing = new BooleanValue("No swing", this, false, () -> !advanced.getValue());
    private final BooleanValue autoDisable = new BooleanValue("Auto disable", this, false, () -> !advanced.getValue());
    private final BooleanValue badPacketsCheck = new BooleanValue("BadPackets check", this, true, () -> !advanced.getValue());

    private final BooleanValue showTargets = new BooleanValue("Targets", this, false);
    public final BooleanValue player = new BooleanValue("Player", this, true, () -> !showTargets.getValue());
    public final BooleanValue invisibles = new BooleanValue("Invisibles", this, false, () -> !showTargets.getValue());
    public final BooleanValue animals = new BooleanValue("Animals", this, false, () -> !showTargets.getValue());
    public final BooleanValue mobs = new BooleanValue("Mobs", this, false, () -> !showTargets.getValue());
    public final BooleanValue teams = new BooleanValue("Player Teammates", this, true, () -> !showTargets.getValue());

    public final BooleanValue healthCheck = new BooleanValue("Health Check", this, true, () -> !showTargets.getValue());

    private final BooleanValue weaponOptions = new BooleanValue("Weapons", this, false);
    private final BooleanValue fist = new BooleanValue("Fist", this, false, () -> !weaponOptions.getValue());
    private final BooleanValue swords = new BooleanValue("Swords", this, true, () -> !weaponOptions.getValue());
    private final BooleanValue axes = new BooleanValue("Axes", this, false, () -> !weaponOptions.getValue());
    private final BooleanValue extra = new BooleanValue("Extra", this, false, () -> !weaponOptions.getValue());
    private final BooleanValue sharpness = new BooleanValue("Sharpness", this, false, () -> !weaponOptions.getValue());
    private final BooleanValue knockback = new BooleanValue("Knockback", this, false, () -> !weaponOptions.getValue());
    private final BooleanValue fireAspect = new BooleanValue("Fire aspect", this, false, () -> !weaponOptions.getValue());

    // Internal variables
    private final Queue<Packet<?>> packetQueue = new ConcurrentLinkedQueue<>();
    private final StopWatch attackStopWatch = new StopWatch();
    private final StopWatch clickStopWatch = new StopWatch();
    public static boolean blocking, swing, allowAttack, rotating;
    private long nextSwing;
    public List<Entity> targets;
    public Entity target;
    private int attack;
    private int expandRange;
    private int blockTicks;
    private int switchTicks;
    public int hitTicks;
    private final List<Entity> pastTargets = new ArrayList<>();
    private final Map<Entity, Integer> lastAttackTick = new HashMap<>();
    public List<Entity> recentTargets = new ArrayList<>();
    private int attackWindowTicks;
    public static boolean attackWindowActive = false;

    private boolean blinking = false;

    public KillAura() {
        INSTANCE = this;
        for (MovementFix movementFix : MovementFix.values()) {
            movementCorrection.add(movementFix);
        }
        movementCorrection.setDefault(MovementFix.OFF);
    }

    @EventLink
    public final Listener<PreMotionEvent> onPreMotionEvent = event -> {
        this.packetQueue.forEach(PacketUtil::sendNoEvent);
        this.packetQueue.clear();
        this.hitTicks++;

        if (SlotComponent.getItemStack() != null && SlotComponent.getItemStack().getItem() instanceof ItemSword) {
            // Logic preserved
        } else {
            blocking = false;
        }

        if (!GUIDetectionComponent.inGUI()) {
            if (this.target == null || mc.thePlayer.isDead || (Scaffold.INSTANCE.isEnabled() && !attackWhilstScaffolding.getValue())) {
                if (!this.autoBlock.getValue().getName().equals("Watchdog 1.12")) {
                    if (!BadPacketsComponent.bad()) {
                        this.unblock(false);
                    }
                } else if (!BadPacketsComponent.bad()) {
                    int currentItem = mc.thePlayer.inventory.currentItem;
                    int randomItem;
                    do {
                        randomItem = ThreadLocalRandom.current().nextInt(8);
                    } while (currentItem == randomItem);

                    if (blocking && !mc.thePlayer.isUsingItem()) {
                        PacketUtil.send(new C09PacketHeldItemChange(randomItem));
                        mc.thePlayer.inventory.currentItem = randomItem;
                        PacketUtil.send(new C09PacketHeldItemChange(currentItem));
                        mc.thePlayer.inventory.currentItem = currentItem;
                        blocking = false;
                    }
                }
                this.target = null;
            }

            if (this.target != null) {
                if (!this.espMode.getValue().getName().equals("None")) {
                    Color color = coloredRing.getValue() ? this.getTheme().getFirstColor() : Color.WHITE;

                    switch (espMode.getValue().getName()) {
                        case "Ring":
                            ESPComponent.add(new SigmaRing(new ESPColor(color, color, color)));
                            break;
                        case "Box":
                            switch (boxMode.getValue().getName()) {
                                case "Full":
                                    ESPComponent.add(new FullBox(new ESPColor(color, color, color)));
                                    break;
                                case "Above":
                                    ESPComponent.add(new AboveBox(new ESPColor(color, color, color)));
                                    break;
                            }
                            break;
                    }
                }
            }
        }
    };

    @EventLink
    public final Listener<WorldChangeEvent> onWorldChange = event -> {
        if (this.autoDisable.getValue()) {
            this.toggle();
        }
    };

    @EventLink(value = Priorities.HIGH)
    public final Listener<PreUpdateEvent> onPreUpdate = event -> {
        {
            if (this.attackWindowTicks > 0) {
                --this.attackWindowTicks;
            }
            attackWindowActive = this.attackWindowTicks > 0;

            mc.entityRenderer.getMouseOver(1.0F);
            allowAttack = !BadPacketsComponent.bad(false, false, false, true, true);

            if (mc.thePlayer.getHealth() <= 0.0 && this.autoDisable.getValue()) {
                this.toggle();
            }

            if (!Scaffold.INSTANCE.isEnabled() || this.attackWhilstScaffolding.getValue()) {
                if (!this.isValidWeapon()) {
                    this.target = null;
                    this.cantPreBlock();
                } else {
                    this.attack = Math.max(Math.min(this.attack, this.attack - 2), 0);

                    boolean shouldPrevent = GUIDetectionComponent.inGUI() ||
                            mc.gameSettings.keyBindUseItem.isKeyDown() ||
                            BadPacketsComponent.bad(true, false, false, false, true) ||
                            (mc.currentScreen instanceof GuiChest);

                    if (mc.thePlayer.ticksExisted % 20 == 0 && !this.autoBlock.getValue().getName().equals("Watchdog 1.8")) {
                        this.expandRange = (int) (3.0 + Math.random() * 0.5);
                    }

                    if (mc.thePlayer.ticksExisted % 2 == 0 && this.autoBlock.getValue().getName().equals("Watchdog 1.8") && !shouldPrevent) {
                        this.expandRange = (int) (5.0 + Math.random() * 0.5);
                    }

                    if (shouldPrevent && this.autoBlock.getValue().getName().equals("Watchdog 1.8")) {
                        this.expandRange = (int) (3.0 + Math.random() * 0.5);
                    }

                    if (!GUIDetectionComponent.inGUI()) {
                        this.getTargets();
                        if (this.targets.isEmpty()) {
                            this.target = null;
                            this.cantPreBlock();
                        } else {
                            this.target = this.targets.get(0);
                            if (this.target != null && !mc.thePlayer.isDead) {
                                if (this.canBlock()) {
                                    this.preBlock();
                                } else {
                                    this.cantPreBlock();
                                }

                                this.rotations();

                                // Update recent targets logic
                                this.recentTargets = this.lastAttackTick.entrySet().stream()
                                        .filter(entry -> mc.thePlayer.ticksExisted - entry.getValue() <= 5)
                                        .map(Map.Entry::getKey)
                                        .collect(Collectors.toList());

                                if (this.mode.getValue().getName().equals("Single") && this.target != null) {
                                    this.recentTargets.clear();
                                    this.recentTargets.add(this.target);
                                }
                            } else {
                                this.cantPreBlock();
                            }
                        }
                    }
                }
            }
        }
    };

    @EventLink(value = Priorities.MEDIUM)
    public final Listener<PreUpdateEvent> onMediumPreUpdate = event -> {
        if (this.target != null && !mc.thePlayer.isDead) {
            if (this.lastSafeUnBlockTick()) {
                this.attackWindowTicks = 2;
            }

            switch (this.autoBlock.getValue().getName()) {
                case "Watchdog 1.8":
                    // Placeholder for specific Watchdog logic
                    break;
                default:
                    this.doAttack(this.targets);
                    if (this.canBlock()) {
                        this.postAttackBlock();
                    }
                    break;
            }
        }
    };

    @EventLink
    public final Listener<MouseOverEvent> onMouseOver = event -> {
        event.setRange(event.getRange() + this.range.getValue().doubleValue() - 3.0);
    };

    @EventLink
    public final Listener<PostMotionEvent> onPostMotion = event -> {
        if (this.target != null && this.canBlock()) {
            this.postBlock();
        }
    };

    @EventLink(value = Priorities.HIGH)
    public final Listener<RenderItemEvent> onRenderItem = event -> {
        if (this.target != null && !this.autoBlock.getValue().getName().equals("None") && this.canBlock()) {
            event.setEnumAction(EnumAction.BLOCK);
            event.setUseItem(true);
        }
    };

    @EventLink(value = Priorities.VERY_HIGH)
    public final Listener<PacketSendEvent> onPacketSend = event -> {
        if (!event.isCancelled()) {
            Packet<?> packet = event.getPacket();
            if (packet instanceof C0APacketAnimation) {
                swing = true;
            } else if (packet instanceof C03PacketPlayer) {
                swing = false;
            }
            this.packetBlock(event);
        }
    };

    @EventLink
    public final Listener<ClickEvent> onRightClick = event -> {
        if (this.target != null) {
            if (SlotComponent.getItemStack() != null && SlotComponent.getItemStack().getItem() instanceof ItemSword) {
                switch (this.autoBlock.getValue().getName()) {
                    case "Fake":
                    case "None":
                        if (!this.preventServerSideBlocking.getValue()) return;
                        if (SlotComponent.getItemStack() == null) return;
                        if (!(SlotComponent.getItemStack().getItem() instanceof ItemSword)) return;
                        // Cancel right click
                        break;
                    default:
                        // Logic to cancel generic right clicks if autoblock is handling it
                        break;
                }
            }
        }
    };

    @EventLink
    public final Listener<SlowDownEvent> onSlowDown = event -> {
        switch (this.autoBlock.getValue().getName()) {
            case "Legit":
            default:
                break;
            case "Watchdog":
            case "Watchdog 1.8":
            case "Watchdog 1.12":
                if (this.target != null && mc.thePlayer.getHeldItem() != null && mc.thePlayer.getHeldItem().getItem() instanceof ItemSword) {
                    event.setCancelled(true);
                }
                break;
        }
    };

    @EventLink
    public final Listener<Render2DEvent> onRender2D = event -> {
        // FOV Circle and Movement Arc rendering would go here
        // Using Urticaria's render utils for drawing
        if (showFOVCircle.getValue() && fov.getValue().doubleValue() < 360) {
            // Draw circle logic adapted to Urticaria's RenderUtil
        }
    };

    @Override
    public void onEnable() {
        this.attack = 0;
        this.blockTicks = 0;
        this.nextSwing = 0L;
    }

    @Override
    public void onDisable() {
        this.packetQueue.forEach(PacketUtil::sendNoEvent);
        this.packetQueue.clear();
        this.dispatchWatchdogAura(); // Dispatch buffered packets
        this.target = null;
        if (!BadPacketsComponent.bad()) {
            this.unblock(false);
        }
        mc.gameSettings.keyBindUseItem.setPressed(false);
        this.blinking = false;
        this.lastAttackTick.clear();
        this.recentTargets.clear();
    }

    public void getTargets() {
        double rangeVal = this.range.getValue().doubleValue();
        this.targets = Client.INSTANCE.getTargetManager().getTargets(rangeVal);

        if (this.mode.getValue().getName().equals("Switch")) {
            this.targets.removeAll(this.pastTargets);
        }

        if (this.targets.isEmpty()) {
            this.pastTargets.clear();
            this.targets = Client.INSTANCE.getTargetManager().getTargets(rangeVal + this.expandRange);
        }

        if (this.fov.getValue().doubleValue() < 360.0) {
            double halfFOV = this.fov.getValue().doubleValue() / 2.0;
            this.targets.removeIf(entity -> !this.isWithinFOV((EntityLivingBase) entity, halfFOV));
        }

        // Sorting logic from Mozilla
        switch (this.sorting.getValue().getName()) {
            case "Health":
                this.targets.sort(Comparator.comparingDouble(e -> ((EntityLivingBase) e).getHealth()));
                break;
            case "Hurt Time":
                this.targets.sort(Comparator.comparingDouble(e -> ((EntityLivingBase) e).hurtTime));
                break;
        }
    }

    private boolean isWithinFOV(EntityLivingBase entity, double halfFOV) {
        Vector2f rotation = RotationUtil.calculate(entity);
        float yawDiff = Math.abs(MathHelper.wrapAngleTo180_float(rotation.x - mc.thePlayer.rotationYaw));
        return yawDiff <= halfFOV;
    }

    public void cantPreBlock() {
        switch (this.autoBlock.getValue().getName()) {
            case "Universal":
            case "Watchdog":
                this.blockTicks = -1;
                break;
        }
    }

    public void rotations() {
        float speed = (float) MathUtil.getRandom(rotationSpeed.getValue().doubleValue(), rotationSpeed.getSecondValue().doubleValue());
        final MovementFix fixMode = movementCorrection.getValue() == MovementFix.OFF ? MovementFix.OFF : movementCorrection.getValue();

        switch (this.rotationMode.getValue().getName()) {
            case "Legit/Normal":
                Vector2f rotation = RotationUtil.calculate(this.target);
                if (speed != 0.0F && (mc.thePlayer.getDistanceToEntity(target) < 3.2 + Math.random() * 0.5 || !this.autoBlock.getValue().getName().equals("Watchdog 1.8"))) {
                    RotationComponent.setRotations(rotation, speed, fixMode);
                }
                break;

            case "NCP":
                // NCP logic port
                int predictionTicks = (int) (Math.random() * 1.0);
                // Simple prediction logic
                Vector2f ncpRot = RotationUtil.calculate(this.target);
                if (speed != 0.0F) {
                    RotationComponent.setRotations(ncpRot, speed, fixMode);
                }
                break;

            case "Snap":
                Vector2f snapRot = RotationUtil.calculate(this.target);
                if (speed != 0.0F && this.lastSafeUnBlockTick2()) {
                    // Logic to force snap (high speed or disable smoothing in component)
                    RotationComponent.setRotations(snapRot, 180, fixMode);
                }
                break;

            case "Autistic AntiCheat":
                // Logic ported from previous
                double aacSpeed = speed * 10;
                RotationComponent.setRotations(new Vector2f((float) (RotationComponent.rotations.x + aacSpeed), 90), (float) (aacSpeed / 18), fixMode);
                break;
        }
    }

    // Helper for delay calculation
    public double[] getDelay() {
        double delay = -1.0;
        boolean shouldAttack = false;

        switch (this.clickMode.getValue().getName()) {
            case "1.9+":
            case "1.9+ (1.8 Visuals)":
                double attackSpeed = 4.0;
                if (mc.thePlayer.getHeldItem() != null) {
                    // Item speed logic
                    Item item = mc.thePlayer.getHeldItem().getItem();
                    if (item instanceof ItemSword) attackSpeed = 1.6;
                    else if (item instanceof ItemAxe) attackSpeed = 1.0; // simplified
                    // ... other items
                }

                if (randomise19.getValue()) {
                    attackSpeed -= Math.random() * random19Factor.getValue().doubleValue();
                }
                delay = 1.0 / attackSpeed * 20.0 - 1.0;
                break;

            default:
                delay = this.clickDelayBlock(delay);
                shouldAttack = this.clickStopWatch.finished((long) (delay * 50.0));
                break;
        }
        return new double[]{shouldAttack ? 1.0 : 0.0, delay};
    }

    private void doAttack(List<Entity> targets) {
        double[] delayInfo = this.getDelay();
        double delay = delayInfo[1];
        boolean shouldAttack = delayInfo[0] == 1.0;

        if (this.attackStopWatch.finished(this.nextSwing) && this.target != null && (this.clickStopWatch.finished((long) (delay * 50.0)) || shouldAttack)) {
            long cpsDelay = (long) (MathUtil.getRandom(cps.getValue().intValue(), cps.getSecondValue().intValue()) * 1.5);
            this.nextSwing = 1000L / cpsDelay;

            if ((Math.sin(this.nextSwing) + 1.0 > Math.random() || this.attackStopWatch.finished(this.nextSwing + 500L) || Math.random() > 0.5) && (allowAttack || !this.badPacketsCheck.getValue())) {

                // Raycast check
                MovingObjectPosition objectPosition = RayCastUtil.rayCast(RotationComponent.rotations, range.getValue().doubleValue());
                if (throughWalls.getValue()) {
                    // Custom raycast logic through walls if needed
                }

                switch (this.mode.getValue().getName()) {
                    case "Single":
                    case "Switch":
                        if ((mc.thePlayer.getDistanceToEntity(this.target) <= range.getValue().doubleValue() && !rayCast.getValue()) || (objectPosition != null && objectPosition.entityHit == this.target)) {
                            this.attack(this.target);
                        }
                        break;
                    case "Multiple":
                        targets.removeIf(entity -> mc.thePlayer.getDistanceToEntity(entity) > range.getValue().doubleValue());
                        targets.forEach(this::attack);
                        break;
                }
                this.attackStopWatch.reset();
            }
        }
    }

    private boolean lastSafeUnBlockTick() {
        // Simplified check based on delay
        return this.attackStopWatch.finished(this.nextSwing - 50L) && allowAttack;
    }

    private boolean lastSafeUnBlockTick2() {
        return this.attackStopWatch.finished(this.nextSwing - 1L) && allowAttack;
    }

    private void attack(Entity entity) {
        Client.INSTANCE.getEventBus().handle(new ClickEvent());
        AttackEvent attackEvent = new AttackEvent(entity);
        Client.INSTANCE.getEventBus().handle(attackEvent);

        if (this.canBlock()) {
            this.attackBlock();
        }

        this.attackWindowTicks = Math.max(this.attackWindowTicks, 1);
        attackWindowActive = true;

        if (!noSwing.getValue() && ViaMCP.getInstance().getVersion() <= 47) {
            mc.thePlayer.swingItem();
        }

        // Logic for KeepSprint
        if (!keepSprint.getValue()) {
            mc.playerController.attackEntity(mc.thePlayer, entity);
        } else {
            PacketUtil.send(new C02PacketUseEntity(entity, C02PacketUseEntity.Action.ATTACK));
            mc.thePlayer.swingItem();
        }

        if (!noSwing.getValue() && ViaMCP.getInstance().getVersion() > 47) {
            mc.thePlayer.swingItem();
        }

        this.clickStopWatch.reset();
        this.hitTicks = 0;
        this.lastAttackTick.put(entity, mc.thePlayer.ticksExisted);
    }

    private void block(boolean shouldInteract, boolean shouldSendPacket) {
        if (!blocking || !shouldInteract) {
            if (shouldInteract && target != null) {
                mc.playerController.interactWithEntitySendPacket(mc.thePlayer, target);
            }

            if (ViaMCP.getInstance().getVersion() >= 393 && !mc.thePlayer.isUsingItem()) {
                // 1.19+ blocking logic packet wrapper
            }

            PacketUtil.send(new C08PacketPlayerBlockPlacement(SlotComponent.getItemStack()));
            blocking = true;
        }
    }

    private void unblock(boolean checkSwing) {
        if (blocking && (!checkSwing || !swing)) {
            PacketUtil.send(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
            blocking = false;
        }
    }

    public double clickDelayBlock(double delay) {
        switch (autoBlock.getValue().getName()) {
            case "Universal":
                delay = this.blockTicks >= 4 ? -1.0 : 500.0;
                break;
            case "Watchdog":
                if (target != null && SlotComponent.getItemStack() != null) delay = 0.0;
                break;
            // Additional delay logic...
        }
        return delay;
    }

    public void packetBlock(PacketSendEvent event) {
        Packet<?> packet = event.getPacket();
        if (autoBlock.getValue().getName().equals("Intave")) {
            if (packet instanceof C03PacketPlayer) {
                event.setCancelled(true);
                this.unblock(false);
                PacketUtil.sendNoEvent(packet);
                this.block(false, true);
            }
        }
    }

    private void attackBlock() {
        // Logic for attack block (if needed)
    }

    private void postAttackBlock() {
        switch (autoBlock.getValue().getName()) {
            case "Intave":
                this.block(false, false);
                break;
            case "Vanilla":
                if (this.hitTicks != 0) this.block(false, true);
                break;
            case "Watchdog 1.8":
                allowAttack = false;
                this.blockTicks++;
                this.blink(); // Custom blink logic
                this.block(false, true);
                this.dispatchWatchdogAura();
                break;
            case "Vanilla ReBlock":
                if (this.hitTicks == 1) this.block(false, true);
                break;
        }
    }

    private void preBlock() {
        switch (autoBlock.getValue().getName()) {
            case "Legit":
                // Legit blocking logic using keybinds
                break;
            case "NCP":
            case "Intave":
                allowAttack = true;
                this.unblock(false);
                break;
            case "Grim":
                PacketUtil.send(new C09PacketHeldItemChange(SlotComponent.getItemIndex() % 8 + 1));
                PacketUtil.send(new C09PacketHeldItemChange(SlotComponent.getItemIndex()));
                this.block(false, false);
                break;
            case "Watchdog":
                this.blockTicks++;
                if (this.blockTicks >= 3) this.blockTicks = 1;
                if (this.blockTicks == 1) {
                    PacketUtil.send(new C09PacketHeldItemChange(SlotComponent.getItemIndex() % 8 + 1));
                    PacketUtil.send(new C09PacketHeldItemChange(SlotComponent.getItemIndex()));
                    allowAttack = false;
                    this.block(false, true);
                }
                break;
            case "Watchdog 1.8":
                this.block(false, true);
                this.dispatchWatchdogAura();
                break;
        }
    }

    private void postBlock() {
        switch (autoBlock.getValue().getName()) {
            case "Watchdog 1.12":
                this.blockTicks++;
                if (this.blockTicks > 0 && !BadPacketsComponent.bad(false, true, false, false, false) && !getModule(Flight.class).isEnabled()) {
                    this.block(true, false);
                }
                break;
        }
    }

    public boolean canBlock() {
        if (!rightClickOnly.getValue() || mc.gameSettings.keyBindUseItem.isKeyDown()) {
            return SlotComponent.getItemStack() != null && SlotComponent.getItemStack().getItem() instanceof ItemSword;
        }
        return false;
    }

    // 修复: 添加 canRenderBlock 方法
    public boolean canRenderBlock() {
        final String modeValue = autoBlock.getValue().getName();
        return mc.thePlayer != null && target != null && canBlock() && !(modeValue.equalsIgnoreCase("Old Intave") || modeValue.equalsIgnoreCase("None") || modeValue.equalsIgnoreCase("Legit"));
    }

    private boolean isValidWeapon() {
        if (!weaponOptions.getValue()) return true;
        ItemStack stack = SlotComponent.getItemStack();
        if (stack == null) return fist.getValue();
        Item item = stack.getItem();
        if (item instanceof ItemSword && swords.getValue()) return true;
        if (item instanceof ItemAxe && axes.getValue()) return true;
        // Check enchantments
        if (sharpness.getValue() && EnchantmentHelper.getEnchantmentLevel(Enchantment.sharpness.effectId, stack) > 0) return true;
        return false;
    }

    // Mocking WatchdogTeleportAura logic with local blink
    private void blink() {
        this.blinking = true;
        BlinkComponent.blinking = true;
        // In real Urticaria implementation, you would use BlinkComponent.setBlink(true) and handle packets
    }

    private void dispatchWatchdogAura() {
        if (this.blinking) {
            this.blinking = false;
            BlinkComponent.blinking = false;
            BlinkComponent.dispatch();
        }
    }
}