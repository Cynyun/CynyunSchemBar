package test;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.scene.event.Touchable;
import arc.scene.ui.layout.WidgetGroup;

/**
 * 临时 UI 验证件：屏幕上的一块半透明矩形。
 * 只负责“画”，不含任何交互、配置或蓝图逻辑。
 * 后续会被 cynyunschembar/ui/BarTable 取代。
 */
public class BarPanel extends WidgetGroup{

    /** 面板尺寸与边距，先用常量，后续由设置项驱动。 */
    public static final float WIDTH = 360f, HEIGHT = 220f, MARGIN = 24f, TOP_OFFSET = 72f;

    public BarPanel(){
        // 关闭变换，避免影响子元素坐标（后续加子元素时保持屏幕坐标语义）
        setTransform(false);
        setSize(WIDTH, HEIGHT);

        // 纯展示层：不接收点击/滚轮，保证不拦截世界点击
        touchable = Touchable.disabled;

        placeAtTopLeft();
    }

    /** 锚定屏幕左上角（y 轴向上，顶部留出 HUD 状态栏高度）。窗口尺寸变化时由外部重调。 */
    public void placeAtTopLeft(){
        setPosition(MARGIN, Core.graphics.getHeight() - HEIGHT - TOP_OFFSET);
    }

    @Override
    public void draw(){
        // 半透明底色。
        // 注意：arc 里 Fill.rect 的 (x, y) 是矩形中心（内部走 Draw.rect，会减掉 w/2、h/2），
        // 而 Lines.rect 的 (x, y) 是左下角。要给一个元素铺底色必须用 crect（corner rect），
        // 否则底色会整体偏移半个宽高，与描边错位。
        Draw.color(0f, 0f, 0f, 0.45f);
        Fill.crect(x, y, width, height);

        // 1px 描边，方便确认边界
        Draw.color(1f, 1f, 1f, 0.35f);
        Lines.stroke(1f);
        Lines.rect(x, y, width, height);

        Draw.reset();
    }
}
