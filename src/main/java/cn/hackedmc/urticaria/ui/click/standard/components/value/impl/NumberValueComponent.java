package cn.hackedmc.urticaria.ui.click.standard.components.value.impl;

import cn.hackedmc.urticaria.Client;
import cn.hackedmc.urticaria.module.impl.render.ClickGUI; // 导入 ClickGUI
import cn.hackedmc.urticaria.util.font.FontManager;
import cn.hackedmc.urticaria.util.gui.GUIUtil;
import cn.hackedmc.urticaria.util.interfaces.InstanceAccess;
import cn.hackedmc.urticaria.util.render.RenderUtil;
import cn.hackedmc.urticaria.ui.click.standard.components.value.ValueComponent;
import cn.hackedmc.urticaria.util.math.MathUtil;
import cn.hackedmc.urticaria.util.vector.Vector2d;
import cn.hackedmc.urticaria.value.Value;
import cn.hackedmc.urticaria.value.impl.NumberValue;
import util.time.StopWatch;

public class NumberValueComponent extends ValueComponent implements InstanceAccess {

    private final static double SLIDER_WIDTH = 100;
    private final double grabberWidth = 5;
    private final StopWatch stopWatch = new StopWatch();
    public boolean grabbed;
    private double percentage;
    private double selector;
    private double renderPercentage;
    private boolean mouseOver;
    private float hoverTime;

    public NumberValueComponent(final Value<?> value) {
        super(value);

        final NumberValue numberValue = (NumberValue) value;

        this.percentage = (-numberValue.getMin().doubleValue() + numberValue.getValue().doubleValue()) / (-numberValue.getMin().doubleValue() + numberValue.getMax().doubleValue());
    }

    @Override
    public void draw(final Vector2d position, final int mouseX, final int mouseY, final float partialTicks) {
        this.position = position;

        // --- 动态字体大小 ---
        int baseSize = 18;
        try {
            baseSize = Client.INSTANCE.getModuleManager().get(ClickGUI.class).fontSize.getValue().intValue();
        } catch (Exception ignored) {}
        int textSize = Math.max(10, baseSize - 6); // 比模块名小一点
        // ------------------

        // Cast
        final NumberValue numberValue = (NumberValue) this.value;

        String value = String.valueOf(numberValue.getValue().doubleValue());

        // 使用 FontManager.getNunito(textSize)
        final float valueWidth = FontManager.getNunito(textSize).width(this.value.getName()) + 7;

        if (value.endsWith(".0")) {
            value = value.replace(".0", "");
        }

        //Used to determine if the mouse is over the slider
        this.mouseOver = GUIUtil.mouseOver(this.position.x + valueWidth - 5, this.position.y - 3.5F, SLIDER_WIDTH + 10, this.height, mouseX, mouseY);
        if (this.mouseOver) {
            hoverTime = Math.min(1, hoverTime + stopWatch.getElapsedTime() / 200.0F);
        } else {
            hoverTime = Math.max(0, hoverTime - stopWatch.getElapsedTime() / 200.0F);
        }

        // Draws name
        FontManager.getNunito(textSize).drawString(this.value.getName(), this.position.x, this.position.y, this.getStandardClickGUI().fontDarkColor.hashCode());

        // Draws value
        FontManager.getNunito(textSize).drawString(value, this.position.x + valueWidth + 105, this.position.y, this.getStandardClickGUI().fontDarkColor.hashCode());

        // Draws background
        RenderUtil.roundedRectangle(this.position.x + valueWidth, this.position.y + 1.5F, SLIDER_WIDTH, 2, 1, getStandardClickGUI().backgroundColor);

        selector = this.position.x + valueWidth;

        if (getStandardClickGUI().animationTime < 0.8) grabbed = false;

        if (grabbed) {
            percentage = mouseX - selector;
            percentage /= SLIDER_WIDTH;
            percentage = Math.max(Math.min(percentage, 1), 0);

            numberValue.setValue((numberValue.getMin().doubleValue() + (numberValue.getMax().doubleValue() - numberValue.getMin().doubleValue()) * percentage));
            numberValue.setValue(MathUtil.roundWithSteps(numberValue.getValue().doubleValue(), numberValue.getDecimalPlaces().floatValue()));
        }

        //Animations
        final int speed = 30;
        for (int i = 0; i <= stopWatch.getElapsedTime(); i++) {
            renderPercentage = (renderPercentage * (speed - 1) + percentage) / speed;
        }

        final double positionX = selector + renderPercentage * 100;
        RenderUtil.roundedRectangle(positionX - grabberWidth / 2.0F, this.position.y, grabberWidth, grabberWidth, grabberWidth / 2.0F, getTheme().getFirstColor());
        stopWatch.reset();
    }

    @Override
    public void click(final int mouseX, final int mouseY, final int mouseButton) {
        if (this.position == null) {
            return;
        }

        final boolean left = mouseButton == 0;

        if (left) {
            if (this.mouseOver) {
                grabbed = true;
            }
        }
    }

    @Override
    public void released() {
        grabbed = false;
    }

    @Override
    public void bloom() {
        if (this.position == null) {
            return;
        }

        final double positionX = selector + renderPercentage * 100;
        RenderUtil.roundedRectangle(positionX - grabberWidth / 2.0F, this.position.y, grabberWidth, grabberWidth, grabberWidth / 2f, getTheme().getFirstColor());
    }

    @Override
    public void key(final char typedChar, final int keyCode) {

    }
}