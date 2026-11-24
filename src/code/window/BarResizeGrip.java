package code;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.input.KeyCode;
import arc.math.Mathf;
import arc.scene.Element;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.event.Touchable;

/**
 * 窗口右下角的缩放手柄。
 *
 * 外观是一块静态点阵：桌面 4×4 个点、手机 3×3 个点。原来的三条 1px 斜线在高 DPI 手机上
 * 只有 1 物理像素，等于看不见，所以改成实心小方块拼的点阵，常驻显示、不靠悬停才变亮。
 *
 * 命中区是以右下角为顶点的正方形（桌面 24、手机 30 逻辑像素），但仍只认其中的
 * “右下三角”，左上那片留白照样能拖窗口——否则手柄会吃掉半块内容区。
 *
 * 窗口自己负责 setSize / 重排；这里只把“用户想要多宽多高”算出来回调出去。
 */
public class BarResizeGrip extends Element{

    public interface Handler{
        /** 拖动过程中持续回调，参数已经夹在屏幕和最小尺寸之间。 */
        void resize(float w, float h);

        /** 松手：窗口可以在这里存尺寸、清临时状态。 */
        void resized();
    }

    private final Element target;
    private final float minW, minH;
    private final Handler handler;
    /** 换算后的实际边长：命中判定与绘制都用它，否则手机上 16px 的三角根本按不到。 */
    private final float gripSize;

    private float startW, startH, startX, startY;
    private boolean dragging;
    /** 指针是否停在这个三角上。由 enter/exit 维护，draw 里读它，不每帧做 hit。 */
    private boolean over;

    public BarResizeGrip(Element target, float minW, float minH, Handler handler){
        this.target = target;
        this.minW = minW;
        this.minH = minH;
        this.handler = handler;

        touchable = Touchable.enabled;
        gripSize = BarMetrics.gripSize();
        setSize(gripSize, gripSize);

        addListener(new InputListener(){
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button){
                if(pointer != 0 || button != KeyCode.mouseLeft) return false;

                dragging = true;
                startW = target.getWidth();
                startH = target.getHeight();
                startX = event.stageX;
                startY = event.stageY;
                // 拖窗口时窗口要压在其他悬浮窗上面
                target.toFront();
                return true; // 拿到 touch focus，鼠标拖出手柄也能继续收事件
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer){
                if(!dragging) return;

                float maxW = Math.max(minW, Core.graphics.getWidth());
                float maxH = Math.max(minH, Core.graphics.getHeight());
                // 往右上拖是变大：x 增加、y 增加，而屏幕坐标 y 向上，所以高度要用减法
                float nw = Mathf.clamp(startW + (event.stageX - startX), minW, maxW);
                float nh = Mathf.clamp(startH - (event.stageY - startY), minH, maxH);
                handler.resize(Mathf.round(nw), Mathf.round(nh));
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button){
                if(!dragging) return;
                dragging = false;
                handler.resized();
            }

            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Element fromActor){
                if(pointer == -1) over = true;
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Element toActor){
                if(pointer == -1) over = false;
            }
        });
    }

    /** 只认右下三角：到右下角的“横 + 纵”距离超过边长就交给窗口去拖。 */
    @Override
    public Element hit(float x, float y, boolean touchable){
        if(touchable && this.touchable == Touchable.disabled) return null;
        if(x < 0f || y < 0f || x >= width || y >= height) return null;
        if((width - x) + y > gripSize) return null;
        return this;
    }

    @Override
    public void draw(){
        float a = parentAlpha;

        // 静态点阵：手机 3×3、桌面 4×4。点用实心方块画，2 逻辑像素在手机上约 4~5 物理像素，
        // 常态 0.75 的亮度在深色战场上也能一眼看到；只有按下时才更亮，悬停不做变化（静态按钮）。
        int dots = BarMetrics.gripDots();
        float dot = BarMetrics.gripDot();
        float gap = BarMetrics.gripDotGap();
        float margin = BarMetrics.gripDotMargin();

        Draw.color(BarTheme.line.r, BarTheme.line.g, BarTheme.line.b, (dragging ? 1f : 0.75f) * a);
        for(int row = 0; row < dots; row++){
            float y0 = margin + row * (dot + gap);
            for(int col = 0; col < dots; col++){
                float x0 = width - margin - dot - col * (dot + gap);
                Fill.crect(x0, y0, dot, dot);
            }
        }
        Draw.reset();
    }
}
