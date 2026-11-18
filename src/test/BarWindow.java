package test;

import arc.Core;
import arc.input.KeyCode;
import arc.math.Mathf;
import arc.scene.Element;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.event.Touchable;
import arc.scene.ui.Button;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Table;

/**
 * 所有悬浮窗的公共部分：线框外观、按住空白处拖动、贴边吸附、窗口尺寸变化后的重定位。
 *
 * 位置存法（B 方案）：每轴独立记“贴边 / 自由”两种状态。距边界 <= SNAP 就判定贴边并真正贴到
 * 0 像素；否则记下在“可移动空间”里的比例，窗口变化时按比例跟随。
 * 比例必须按 (W - 窗口宽) 归一化，这样 0/1 正好对应两条边界；按整屏宽归一化会让比例永远到不了 1。
 */
public abstract class BarWindow extends Table{

    /** 吸附阈值：松手时离边界 <= 这个距离就贴边，目标 0 像素（真贴边，不留缝）。 */
    public static final float SNAP = 10f;

    private boolean dragging;
    private float dragLastX, dragLastY;

    protected BarWindow(float width, float height){
        // Table 负责子元素布局；关闭变换后 Group.draw 用“父坐标偏移”的方式画子元素，
        // 所以这里的 x/y 仍是屏幕坐标，不必手动换算。
        setTransform(false);
        setSize(width, height);

        // 必须是 enabled：childrenOnly 会让 Element.hit 在空白处直接返回 null，事件到不了窗口，
        // 拖动就无从谈起。代价是窗口矩形内的空白处不再允许穿透建造——悬浮窗本来也不该穿透。
        touchable = Touchable.enabled;
        installDrag();
    }

    /** 这个窗口的位置存在哪（由 BarConfig 提供）。 */
    protected abstract BarConfig.WindowState state();

    /** 还没有落盘位置时（首次安装 / 恢复默认）的落点。 */
    protected abstract void defaultSpawn();

    @Override
    public void draw(){
        float a = parentAlpha;
        // 底色浓度由设置窗的“不透明度”滑条控制，默认 0.40，只压暗一点战场
        BarTheme.fill(x, y, width, height, BarTheme.panelOpacity * a);
        BarTheme.frame(x, y, width + 1f, height + 1f, 0.45f * a); // +1 确保右/上边框包住填充

        // 交给 Table 画子元素（validate + 布局 + 绘制）
        super.draw();
    }

    // ------------------------------------------------------------------ 拖动

    private void installDrag(){
        addListener(new InputListener(){
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button){
                if(pointer != 0 || button != KeyCode.mouseLeft) return false;
                if(isInteractive(event.targetActor)) return false;

                // 点击后的 ui 位于上方
                toFront();

                // 用 stage 坐标的“增量”移动，不去换算父坐标：hudGroup 若带缩放/偏移，增量依然成立
                dragging = true;
                dragLastX = event.stageX;
                dragLastY = event.stageY;
                return true; // 返回 true 后 Scene 会 addTouchFocus，鼠标拖出窗口也能继续收 move 事件
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer){
                if(!dragging) return;
                dragBy(event.stageX - dragLastX, event.stageY - dragLastY);
                dragLastX = event.stageX;
                dragLastY = event.stageY;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button){
                if(!dragging) return;
                dragging = false;
                // 松手才判定贴边，拖动中途不判定，否则经过边界时会来回抖
                captureAnchor(true);
            }
        });
    }

    /** 命中的元素只要在交互件里（含其子元素），就不触发拖动。 */
    private boolean isInteractive(Element target){
        for(Element e = target; e != null && e != this; e = e.parent){
            if(e instanceof Button || e instanceof ScrollPane || e instanceof BarControl || e instanceof BarResizeGrip) return true;
        }
        return false;
    }

    /** 按增量移动，并夹在屏幕内，避免被拖到看不见的地方。 */
    private void dragBy(float dx, float dy){
        float maxX = Math.max(0f, Core.graphics.getWidth() - width);
        float maxY = Math.max(0f, Core.graphics.getHeight() - height);
        // 必须落到整数像素：x/y 带小数时，1px 描边和底色会被光栅化到不同像素上，
        // 看起来就是“一拖动，外框相对底色偏了 1px”。
        setPosition(Mathf.round(Mathf.clamp(x + dx, 0f, maxX)), Mathf.round(Mathf.clamp(y + dy, 0f, maxY)));
    }

    // ------------------------------------------------------------------ 布局

    /** 窗口尺寸变化后调用：贴边的继续贴边，自由的按比例跟随，最后统一夹在窗口内。 */
    public void applyLayout(){
        float w = Core.graphics.getWidth(), h = Core.graphics.getHeight();
        // 最小化/切换全屏的瞬间可能拿到 0 尺寸，直接忽略这一次
        if(w <= 0f || h <= 0f) return;

        BarConfig.WindowState st = state();
        if(!st.present){
            defaultSpawn();
            captureAnchor(false);
            return;
        }

        float availW = Math.max(0f, w - width);
        float availH = Math.max(0f, h - height);

        float nx = st.anchorX < 0 ? 0f : st.anchorX > 0 ? availW : Mathf.round(st.ux * availW);
        float ny = st.anchorY < 0 ? 0f : st.anchorY > 0 ? availH : Mathf.round(st.uy * availH);

        setPosition(Mathf.clamp(nx, 0f, availW), Mathf.clamp(ny, 0f, availH));
    }

    /**
     * 记录当前位置：离边界 <= SNAP 的轴标记为贴边并吸到边界，其余轴存归一化比例。
     *
     * @param persist true = 用户拖动结束后调用，顺手写盘；false = 初始化/恢复默认，不写。
     */
    private void captureAnchor(boolean persist){
        BarConfig.WindowState st = state();
        float availW = Math.max(0f, Core.graphics.getWidth() - width);
        float availH = Math.max(0f, Core.graphics.getHeight() - height);

        st.ux = availW > 0f ? Mathf.clamp(x / availW, 0f, 1f) : 0f;
        st.uy = availH > 0f ? Mathf.clamp(y / availH, 0f, 1f) : 0f;

        st.anchorX = x <= SNAP ? -1 : x >= availW - SNAP ? 1 : 0;
        st.anchorY = y <= SNAP ? -1 : y >= availH - SNAP ? 1 : 0;
        st.present = true;

        // 只动贴边的轴：自由轴保持原值，避免取整误差导致松手瞬间位置微跳
        float nx = st.anchorX < 0 ? 0f : st.anchorX > 0 ? availW : x;
        float ny = st.anchorY < 0 ? 0f : st.anchorY > 0 ? availH : y;
        setPosition(nx, ny);

        if(persist) BarConfig.save();
    }

    /** 恢复默认位置：丢掉已存的状态，回到 defaultSpawn()。 */
    public void resetToDefault(){
        state().present = false;
        applyLayout();
    }

    public boolean isDragging(){
        return dragging;
    }
}
