package cn.hackedmc.urticaria.module.impl.render;

import cn.hackedmc.urticaria.Client;
import cn.hackedmc.urticaria.module.impl.render.interfaces.*;
import cn.hackedmc.urticaria.newevent.annotations.EventLink;
import cn.hackedmc.urticaria.newevent.impl.render.Render2DEvent;
import cn.hackedmc.urticaria.util.font.FontManager;
import cn.hackedmc.urticaria.util.localization.Localization;
import cn.hackedmc.urticaria.Type;
import cn.hackedmc.urticaria.module.Module;
import cn.hackedmc.urticaria.module.api.Category;
import cn.hackedmc.urticaria.module.api.ModuleInfo;
import cn.hackedmc.urticaria.newevent.Listener;
import cn.hackedmc.urticaria.newevent.impl.motion.PreUpdateEvent;
import cn.hackedmc.urticaria.util.font.Font;
import cn.hackedmc.urticaria.util.math.MathUtil;
import cn.hackedmc.urticaria.util.shader.RiseShaders;
import cn.hackedmc.urticaria.util.vector.Vector2d;
import cn.hackedmc.urticaria.util.vector.Vector2f;
import cn.hackedmc.urticaria.value.Value;
import cn.hackedmc.urticaria.value.impl.BooleanValue;
import cn.hackedmc.urticaria.value.impl.DragValue;
import cn.hackedmc.urticaria.value.impl.ModeValue;
import cn.hackedmc.urticaria.value.impl.NumberValue;
import cn.hackedmc.urticaria.value.impl.SubMode;
import cn.hackedmc.fucker.Fucker;
import lombok.Getter;
import lombok.Setter;
import util.time.StopWatch;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.stream.Collectors;

@Getter
@Setter
@ModuleInfo(name = "module.render.interface.name", description = "module.render.interface.description", category = Category.RENDER, autoEnabled = true)
public final class Interface extends Module {
    public static Interface INSTANCE;

    private final ModeValue mode = new ModeValue("Mode", this, () -> Client.CLIENT_TYPE != Type.BASIC) {{
        add(new ModernInterface("Modern", (Interface) this.getParent()));
        add(new NovoInterface("Novo", (Interface) this.getParent()));
        add(new UrticariaInterface("Urticaria", (Interface) this.getParent()));
        add(new WurstInterface("Wurst", (Interface) this.getParent()));
        setDefault("Novo");
    }};

    public final DragValue logoDrag = new DragValue("", this, new Vector2d(200, 200), () -> !mode.getValue().getName().equalsIgnoreCase("urticaria"));

    private final ModeValue modulesToShow = new ModeValue("Modules to Show", this, () -> Client.CLIENT_TYPE != Type.BASIC) {{
        add(new SubMode("All"));
        add(new SubMode("Exclude render"));
        add(new SubMode("Only bound"));
        setDefault("All");
    }};

    // --- 字体大小设置 ---
    public final NumberValue watermarkSize = new NumberValue("Watermark Size", this, 18, 10, 60, 1);
    public final NumberValue arraylistSize = new NumberValue("Arraylist Size", this, 18, 10, 40, 1);
    // --------------------

    public final BooleanValue irc = new BooleanValue("Show IRC Message", this, true);
    public final BooleanValue limitChatWidth = new BooleanValue("Limit Chat Width", this, false);
    public final BooleanValue smoothHotBar = new BooleanValue("Smooth Hot Bar", this, true);

    public BooleanValue suffix = new BooleanValue("Suffix", this, true, () -> Client.CLIENT_TYPE != Type.BASIC);
    public BooleanValue lowercase = new BooleanValue("Lowercase", this, false, () -> Client.CLIENT_TYPE != Type.BASIC);
    public BooleanValue removeSpaces = new BooleanValue("Remove Spaces", this, true, () -> Client.CLIENT_TYPE != Type.BASIC);

    public ModeValue notifyMode = new ModeValue("Notification Mode", this)
            .add(new SubMode("Off"))
            .add(new SubMode("Basic"))
            .add(new SubMode("Central"))
            .setDefault("Off");

    public BooleanValue showToggle = new BooleanValue("Show Toggle", this, true, () -> notifyMode.getValue().getName().equalsIgnoreCase("Off"));

    public BooleanValue shaders = new BooleanValue("Shaders", this, true);
    public BooleanValue lessShaders = new BooleanValue("Less Shaders", this, false, () -> !shaders.getValue());

    private ArrayList<ModuleComponent> allModuleComponents = new ArrayList<>(),
            activeModuleComponents = new ArrayList<>();
    private SubMode lastFrameModulesToShow = (SubMode) modulesToShow.getValue();

    private final StopWatch stopwatch = new StopWatch();
    private final StopWatch updateTags = new StopWatch();

    // 字体对象（去掉 final 以便更新）
    private Font watermarkFont;
    public Font font;
    public Font font2;
    public Font widthComparator;

    public float moduleSpacing = 12, edgeOffset;

    // 缓存状态，检测是否需要刷新
    private int lastWatermarkSize = -1;
    private int lastArraylistSize = -1;

    public Interface() {
        INSTANCE = this;
        // 不要在构造函数里初始化字体，因为此时 OpenGL 上下文可能未就绪
        // createArrayList() 会在第一次 onRender2D 中被调用
    }

    /**
     * 更新字体对象、间距，并重建模块列表以应用新的宽度计算
     */
    private void updateFonts() {
        int wmSize = watermarkSize.getValue().intValue();
        int alSize = arraylistSize.getValue().intValue();

        this.lastWatermarkSize = wmSize;
        this.lastArraylistSize = alSize;

        // 重新加载字体
        this.watermarkFont = FontManager.getProductSansRegular(wmSize);
        this.font = FontManager.getProductSansMedium(alSize);
        this.font2 = FontManager.getProductSansMedium(alSize - 2);

        // 更新用于计算宽度的字体引用
        this.widthComparator = this.font;

        // 动态计算间距 (字体高度 + 2像素缓冲)
        this.moduleSpacing = this.font.height() + 2;

        // 关键：必须重建 ArrayList，否则旧的 Component 会保留旧的宽度和旧的字体引用
        createArrayList();
        sortArrayList();
    }

    public void createArrayList() {
        allModuleComponents.clear();

        // 防止未初始化时的空指针
        if (widthComparator == null) return;

        Client.INSTANCE.getModuleManager().getAll().stream()
                .sorted(Comparator.comparingDouble(module -> -widthComparator.width(Localization.get(module.getDisplayName()))))
                .forEach(module -> allModuleComponents.add(new ModuleComponent(module)));
    }

    public void sortArrayList() {
        if (allModuleComponents.isEmpty()) return;

        activeModuleComponents = allModuleComponents.stream()
                .filter(moduleComponent -> moduleComponent.getModule().shouldDisplay(this))
                .sorted(Comparator.comparingDouble(module -> -module.getNameWidth() - module.getTagWidth()))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    StopWatch lastUpdate = new StopWatch();

    @EventLink()
    public final Listener<PreUpdateEvent> onPreUpdate = event -> {
        if (lastUpdate.finished(1000)) {
            threadPool.execute(() -> {
                for (final ModuleComponent moduleComponent : allModuleComponents) {
                    moduleComponent.setTranslatedName(Localization.get(moduleComponent.getModule().getDisplayName()));
                }
            });
        }
    };

    @EventLink()
    public final Listener<Render2DEvent> onRender2D = event -> {
        // --- 核心修复：检查并更新字体 ---
        // 第一次运行或设置改变时触发
        if (font == null ||
                watermarkSize.getValue().intValue() != lastWatermarkSize ||
                arraylistSize.getValue().intValue() != lastArraylistSize) {
            updateFonts();
        }
        // -----------------------------

        if (lessShaders.getValue() && shaders.getValue()) {
            RiseShaders.GAUSSIAN_BLUR_SHADER.setTryLessRender(true);
            RiseShaders.POST_BLOOM_SHADER.setTryLessRender(true);
            RiseShaders.UI_BLOOM_SHADER.setTryLessRender(true);
            RiseShaders.UI_POST_BLOOM_SHADER.setTryLessRender(true);
        } else if (shaders.getValue()) {
            RiseShaders.GAUSSIAN_BLUR_SHADER.setTryLessRender(false);
            RiseShaders.POST_BLOOM_SHADER.setTryLessRender(false);
            RiseShaders.UI_BLOOM_SHADER.setTryLessRender(false);
            RiseShaders.UI_POST_BLOOM_SHADER.setTryLessRender(false);
        }

        Color logoColor = this.getTheme().getFirstColor();

        final String name =  "Urticaria";
        final String rank = "IDEA READY";
        final String online = "Offline State";

        // 使用动态的 watermarkFont 进行绘制
        if (watermarkFont != null) {
            this.watermarkFont.drawStringWithShadow(name, event.getScaledResolution().getScaledWidth() - this.watermarkFont.width(name) - 2, event.getScaledResolution().getScaledHeight() - this.watermarkFont.height() * 3 - 2, new Color(-1).getRGB());
            this.watermarkFont.drawStringWithShadow(rank, event.getScaledResolution().getScaledWidth() - this.watermarkFont.width(rank) - 2, event.getScaledResolution().getScaledHeight() - this.watermarkFont.height() * 2 - 2, new Color(-1).getRGB());
            this.watermarkFont.drawStringWithShadow(online, event.getScaledResolution().getScaledWidth() - this.watermarkFont.width(online) - 2, event.getScaledResolution().getScaledHeight() - this.watermarkFont.height() - 2, new Color(-1).getRGB());

            this.watermarkFont.drawStringWithShadow("Username:", event.getScaledResolution().getScaledWidth() - this.watermarkFont.width(name) - this.watermarkFont.width("Username:") - 4, event.getScaledResolution().getScaledHeight() - this.watermarkFont.height() * 3 - 2, logoColor.getRGB());
            this.watermarkFont.drawStringWithShadow("Rank:", event.getScaledResolution().getScaledWidth() - this.watermarkFont.width(rank) - this.watermarkFont.width("Rank:") - 4, event.getScaledResolution().getScaledHeight() - this.watermarkFont.height() * 2 - 2, logoColor.getRGB());
            this.watermarkFont.drawStringWithShadow("Online:", event.getScaledResolution().getScaledWidth() - this.watermarkFont.width(online) - this.watermarkFont.width("Online:") - 4, event.getScaledResolution().getScaledHeight() - this.watermarkFont.height() - 2, logoColor.getRGB());
        }

        // 更新动画状态
        for (final ModuleComponent moduleComponent : allModuleComponents) {
            if (moduleComponent.getModule().isEnabled()) {
                moduleComponent.animationTime = Math.min(moduleComponent.animationTime + stopwatch.getElapsedTime() / 100.0F, 10);
            } else {
                moduleComponent.animationTime = Math.max(moduleComponent.animationTime - stopwatch.getElapsedTime() / 100.0F, 0);
            }
        }

        threadPool.execute(() -> {
            if (updateTags.finished(50)) {
                updateTags.reset();

                for (final ModuleComponent moduleComponent : activeModuleComponents) {
                    if (moduleComponent.animationTime == 0) {
                        continue;
                    }

                    for (final Value<?> value : moduleComponent.getModule().getValues()) {
                        if (value instanceof ModeValue) {
                            final ModeValue modeValue = (ModeValue) value;
                            moduleComponent.setTag(modeValue.getValue().getName());
                            break;
                        }
                        moduleComponent.setTag("");
                    }
                }

                this.sortArrayList();
            }

            final float screenWidth = event.getScaledResolution().getScaledWidth();
            final Vector2f position = new Vector2f(0, 0);

            // 确保 activeModuleComponents 是最新的
            for (final ModuleComponent moduleComponent : activeModuleComponents) {
                if (moduleComponent.animationTime == 0) {
                    continue;
                }

                moduleComponent.targetPosition = new Vector2d(screenWidth - moduleComponent.getNameWidth() - moduleComponent.getTagWidth(), position.getY());

                if (!moduleComponent.getModule().isEnabled() && moduleComponent.animationTime < 10) {
                    moduleComponent.targetPosition = new Vector2d(screenWidth + moduleComponent.getNameWidth() + moduleComponent.getTagWidth(), position.getY());
                } else {
                    // 使用动态更新后的 moduleSpacing
                    position.setY(position.getY() + moduleSpacing);
                }

                float offsetX = edgeOffset;
                float offsetY = edgeOffset;

                moduleComponent.targetPosition.x -= offsetX;
                moduleComponent.targetPosition.y += offsetY;

                if (Math.abs(moduleComponent.getPosition().getX() - moduleComponent.targetPosition.x) > 0.5 || Math.abs(moduleComponent.getPosition().getY() - moduleComponent.targetPosition.y) > 0.5 || (moduleComponent.animationTime != 0 && moduleComponent.animationTime != 10)) {
                    for (int i = 0; i < stopwatch.getElapsedTime(); ++i) {
                        moduleComponent.position.x = MathUtil.lerp(moduleComponent.position.x, moduleComponent.targetPosition.x, 1.5E-2F);
                        moduleComponent.position.y = MathUtil.lerp(moduleComponent.position.y, moduleComponent.targetPosition.y, 1.5E-2F);
                    }
                } else {
                    moduleComponent.position = moduleComponent.targetPosition;
                }
            }

            stopwatch.reset();
        });
    };
}