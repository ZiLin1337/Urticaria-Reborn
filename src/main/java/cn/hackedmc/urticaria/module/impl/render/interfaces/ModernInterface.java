package cn.hackedmc.urticaria.module.impl.render.interfaces;

import cn.hackedmc.urticaria.Client;
import cn.hackedmc.urticaria.Type;
import cn.hackedmc.urticaria.component.impl.render.ParticleComponent;
import cn.hackedmc.urticaria.module.impl.render.Interface;
import cn.hackedmc.urticaria.newevent.Listener;
import cn.hackedmc.urticaria.newevent.annotations.EventLink;
import cn.hackedmc.urticaria.newevent.impl.other.KillEvent;
import cn.hackedmc.urticaria.newevent.impl.other.TickEvent;
import cn.hackedmc.urticaria.newevent.impl.render.Render2DEvent;
import cn.hackedmc.urticaria.util.font.Font;
import cn.hackedmc.urticaria.util.font.FontManager;
import cn.hackedmc.urticaria.util.render.ColorUtil;
import cn.hackedmc.urticaria.util.render.RenderUtil;
import cn.hackedmc.urticaria.util.render.particle.Particle;
import cn.hackedmc.urticaria.util.vector.Vector2d;
import cn.hackedmc.urticaria.util.vector.Vector2f;
import cn.hackedmc.urticaria.value.Mode;
import cn.hackedmc.urticaria.value.impl.BooleanValue;
import cn.hackedmc.urticaria.value.impl.ModeValue;
import cn.hackedmc.urticaria.value.impl.SubMode;
import net.minecraft.client.gui.GuiChat;
import util.time.StopWatch;

import java.awt.*;

public class ModernInterface extends Mode<Interface> {

    // 移除写死的字体大小
    // private final Font productSansRegular = FontManager.getProductSansRegular(18);
    private final Font minecraft = FontManager.getMinecraft();

    // 默认给一个字体防止空指针，但后续会被覆盖
    private Font arrayListFont = FontManager.getProductSansRegular(18);

    private final StopWatch stopWatch = new StopWatch();

    private final ModeValue colorMode = new ModeValue("ArrayList Color Mode", this, () -> Client.CLIENT_TYPE != Type.BASIC) {{
        add(new SubMode("Static"));
        add(new SubMode("Fade"));
        add(new SubMode("Breathe"));
        setDefault("Fade");
    }};

    private final ModeValue font = new ModeValue("ArrayList Font", this, () -> Client.CLIENT_TYPE != Type.BASIC) {{
        add(new SubMode("Product Sans"));
        add(new SubMode("Minecraft"));
        setDefault("Product Sans");
    }};

    private final ModeValue shader = new ModeValue("Shader Effect", this, () -> Client.CLIENT_TYPE != Type.BASIC) {{
        add(new SubMode("Glow"));
        add(new SubMode("Shadow"));
        add(new SubMode("None"));
        setDefault("Shadow");
    }};

    private final BooleanValue dropShadow = new BooleanValue("Drop Shadow", this, true, () -> Client.CLIENT_TYPE != Type.BASIC);
    private final BooleanValue sidebar = new BooleanValue("Sidebar", this, true, () -> Client.CLIENT_TYPE != Type.BASIC);
    private final BooleanValue particles = new BooleanValue("Particles on Kill", this, true, () -> Client.CLIENT_TYPE != Type.BASIC);
    private final ModeValue background = new ModeValue("BackGround", this, () -> Client.CLIENT_TYPE != Type.BASIC) {{
        add(new SubMode("Off"));
        add(new SubMode("Normal"));
        add(new SubMode("Blur"));
        setDefault("Normal");
    }};
    private boolean glow, shadow;
    private boolean normalBackGround, blurBackGround;
    private String username, coordinates;
    // float nameWidth, userWidth, xyzWidth; // 这些变量没被用到或者计算方式有问题，我这里直接在用的时候算
    private Color logoColor;

    public ModernInterface(String name, Interface parent) {
        super(name, parent);
    }

    @EventLink()
    public final Listener<Render2DEvent> onRender2D = event -> {

        if (mc == null || mc.gameSettings.showDebugInfo || mc.theWorld == null || mc.thePlayer == null || Client.CLIENT_TYPE != Type.BASIC) {
            return;
        }

        // --- 核心修改：动态获取字体 ---
        // 如果用户选了 "Product Sans"，就用父类(Interface)里动态计算的 font
        if (this.font.getValue().getName().equals("Product Sans")) {
            // 注意判空
            if (this.getParent().font != null) {
                this.arrayListFont = this.getParent().font;
            }
        } else {
            this.arrayListFont = minecraft;
        }
        // ---------------------------

        // 使用当前字体的实际高度作为间距
        this.getParent().setModuleSpacing(this.arrayListFont.height() + 2);
        this.getParent().setWidthComparator(this.arrayListFont);
        this.getParent().setEdgeOffset(10);

        // modules in the top right corner of the screen
        for (final ModuleComponent moduleComponent : this.getParent().getActiveModuleComponents()) {
            if (moduleComponent.animationTime == 0) {
                continue;
            }

            String name = (this.getParent().lowercase.getValue() ? moduleComponent.getTranslatedName().toLowerCase() : moduleComponent.getTranslatedName()).replace(getParent().getRemoveSpaces().getValue() ? " " : "", "");

            String tag = (this.getParent().lowercase.getValue() ? moduleComponent.getTag().toLowerCase() : moduleComponent.getTag())
                    .replace(getParent().getRemoveSpaces().getValue() ? " " : "", "");

            final double x = moduleComponent.getPosition().getX();
            final double y = moduleComponent.getPosition().getY();
            final Color finalColor = moduleComponent.getColor();

            final double widthOffset = arrayListFont == minecraft ? 3.5 : 2;

            if (this.normalBackGround || this.blurBackGround) {
                Runnable backgroundRunnable = () ->
                        RenderUtil.rectangle(x + 0.5 - widthOffset, y - 2.5,
                                (moduleComponent.nameWidth + moduleComponent.tagWidth) + 2 + widthOffset,
                                this.getParent().moduleSpacing, getTheme().getBackgroundShade());

                if (this.normalBackGround) {
                    NORMAL_RENDER_RUNNABLES.add(backgroundRunnable);
                }

                if (this.blurBackGround) {
                    NORMAL_BLUR_RUNNABLES.add(() ->
                            RenderUtil.rectangle(x + 0.5 - widthOffset, y - 2.5,
                                    (moduleComponent.nameWidth + moduleComponent.tagWidth) + 2 + widthOffset,
                                    this.getParent().moduleSpacing, Color.BLACK));
                }

                // Draw the glow/shadow around the module
                NORMAL_POST_BLOOM_RUNNABLES.add(() -> {
                    if (glow || shadow) {

                        RenderUtil.rectangle(x + 0.5 - widthOffset, y - 2.5,
                                (moduleComponent.nameWidth + moduleComponent.tagWidth) + 2 + widthOffset,
                                this.getParent().moduleSpacing, glow ? ColorUtil.withAlpha(finalColor, 164) : getTheme().getDropShadow());
                    }
                });
            }

            final boolean hasTag = !moduleComponent.getTag().isEmpty() && this.getParent().suffix.getValue();

            Runnable textRunnable = () -> {
                if (dropShadow.getValue()) {
                    arrayListFont.drawStringWithShadow(name, x, y, finalColor.getRGB());

                    if (hasTag) {
                        arrayListFont.drawStringWithShadow(tag, x + moduleComponent.getNameWidth() + 3, y, 0xFFCCCCCC);
                    }
                } else {
                    arrayListFont.drawString(name, x, y, finalColor.getRGB());

                    if (hasTag) {
                        arrayListFont.drawString(tag, x + moduleComponent.getNameWidth() + 3, y, 0xFFCCCCCC);
                    }
                }
            };

            Runnable shadowRunnable = () -> {
                arrayListFont.drawString(name, x, y, Color.BLACK.getRGB());

                if (hasTag) {
                    arrayListFont.drawString(tag, x + moduleComponent.getNameWidth() + 3, y, Color.BLACK.getRGB());
                }
            };

            NORMAL_RENDER_RUNNABLES.add(textRunnable);

            if (glow) {
                NORMAL_POST_BLOOM_RUNNABLES.add(textRunnable);
            } else if (shadow) {
                NORMAL_POST_BLOOM_RUNNABLES.add(shadowRunnable);
            }

            if (this.sidebar.getValue()) {
                // Sidebar height should match spacing or be slightly less
                float barHeight = this.getParent().moduleSpacing - 3; // 动态高度
                Runnable runnable = () -> RenderUtil.roundedRectangle(x + moduleComponent.getNameWidth() + moduleComponent.getTagWidth() + 2, y - 1.5f,
                        2, barHeight, 1, finalColor);

                runnable.run();
                NORMAL_POST_BLOOM_RUNNABLES.add(runnable);
            }
        }

        if (coordinates == null || username == null) return;

        // --- 底部信息渲染 ---
        // 注意：原代码这里也用了写死的字体，这里不改的话左下角还是小的。
        // 如果你想让左下角也跟随大小变化，可以用 arrayListFont，或者用 getParent().font2

        Font bottomFont = this.arrayListFont; // 或者用 this.getParent().font;

        NORMAL_POST_BLOOM_RUNNABLES.add(() -> {
            if (glow || shadow) {
                // coordinates of user in the bottom left corner of the screen
                final float y = event.getScaledResolution().getScaledHeight() - bottomFont.height() - 1;
                final float coordX = 5;

                // 这里的渲染原代码混用了 regular 和 medium，这里简化为统一使用当前字体
                bottomFont.drawStringWithShadow("XYZ:", coordX, y - (mc.currentScreen instanceof GuiChat ? 13 : 0), 0xFFCCCCCC);
                bottomFont.drawStringWithShadow(coordinates, coordX + bottomFont.width("XYZ:") + 2, y - (mc.currentScreen instanceof GuiChat ? 13 : 0), 0xFFCCCCCC);
            }

            if (!stopWatch.finished(2000)) {
                ParticleComponent.render();
            }
        });

        // coordinates of user in the bottom left corner of the screen
        final float y = event.getScaledResolution().getScaledHeight() - bottomFont.height() - 1;
        final float coordX = 5;
        bottomFont.drawStringWithShadow("XYZ:", coordX, y - (mc.currentScreen instanceof GuiChat ? 13 : 0), 0xFFCCCCCC);
        bottomFont.drawStringWithShadow(coordinates, coordX + bottomFont.width("XYZ:") + 2, y - (mc.currentScreen instanceof GuiChat ? 13 : 0), 0xFFCCCCCC);

        NORMAL_POST_BLOOM_RUNNABLES.add(() -> {
            //shadow
        });

        if (mc.thePlayer.ticksExisted % 150 == 0) {
            stopWatch.reset();
        }
    };

    @EventLink()
    public final Listener<KillEvent> onKill = event -> {
        if (Client.CLIENT_TYPE != Type.BASIC) return;

        if (!stopWatch.finished(2000) && this.particles.getValue()) {
            for (int i = 0; i <= 10; i++) {
                ParticleComponent.add(new Particle(new Vector2f(0, 0),
                        new Vector2f((float) Math.random(), (float) Math.random())));
            }
        }

        stopWatch.reset();
    };

    @EventLink()
    public final Listener<TickEvent> onTick = event -> {
        if (Client.CLIENT_TYPE != Type.BASIC || !mc.getNetHandler().doneLoadingTerrain) return;

        if (mc.thePlayer == null || mc.theWorld == null) return;

        glow = this.shader.getValue().getName().equals("Glow");
        shadow = this.shader.getValue().getName().equals("Shadow");

        // onTick 里也更新一次字体引用，双重保险
        if (this.font.getValue().getName().equals("Product Sans")) {
            if (this.getParent().font != null) {
                this.arrayListFont = this.getParent().font;
            }
        } else {
            this.arrayListFont = minecraft;
        }

        username = mc.getSession() == null || mc.getSession().getUsername() == null ? "null" : mc.getSession().getUsername();
        coordinates = (int) mc.thePlayer.posX + ", " + (int) mc.thePlayer.posY + ", " + (int) mc.thePlayer.posZ;

        logoColor = this.getTheme().getFirstColor();

        normalBackGround = background.getValue().getName().equals("Normal");
        blurBackGround = normalBackGround || background.getValue().getName().equals("Blur");

        // modules in the top right corner of the screen
        for (final ModuleComponent moduleComponent : this.getParent().getActiveModuleComponents()) {
            if (moduleComponent.animationTime == 0) {
                continue;
            }

            final boolean hasTag = !moduleComponent.getTag().isEmpty() && this.getParent().suffix.getValue();

            String name = (this.getParent().lowercase.getValue() ? moduleComponent.getTranslatedName().toLowerCase() : moduleComponent.getTranslatedName()).replace(getParent().getRemoveSpaces().getValue() ? " " : "", "");

            String tag = (this.getParent().lowercase.getValue() ? moduleComponent.getTag().toLowerCase() : moduleComponent.getTag())
                    .replace(getParent().getRemoveSpaces().getValue() ? " " : "", "");

            Color color = this.getTheme().getFirstColor();
            switch (this.colorMode.getValue().getName()) {
                case "Breathe": {
                    double factor = this.getTheme().getBlendFactor(new Vector2d(0, 0));
                    color = ColorUtil.mixColors(this.getTheme().getFirstColor(), this.getTheme().getSecondColor(), factor);
                    break;
                }
                case "Fade": {
                    color = this.getTheme().getAccentColor(new Vector2d(0, moduleComponent.getPosition().getY()));
                    break;
                }
            }

            moduleComponent.setColor(color);

            // 使用当前的 arrayListFont 计算宽度
            moduleComponent.setNameWidth(arrayListFont.width(name));
            moduleComponent.setTagWidth(hasTag ? (arrayListFont.width(tag) + 4) : 0);
        }
    };
}