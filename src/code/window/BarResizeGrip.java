package code;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.input.KeyCode;
import arc.math.Mathf;
import arc.scene.Element;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.event.Touchable;

/**
 * 窗口右下角的缩放手柄。
 *
 * 只有 16px 正方形里的“右下三角”响应，左上那片留白仍然可以拖动窗口——否则手柄会占掉
 * 一大块本该能拖的区域。三条 1px 斜线从左下指向右上，长度 6/10/14，正好铺满这个三角。
 *
 * 窗口自己负责 setSize / 重排；这里只把“用户想要多宽多高”算出来回调出去。
 */
public class BarResizeGrip extends Element{

    /** 手柄正方形的边长（逻辑像素，实际用 BarTheme.u 换算）。 */
    public static final float SIZE = 16f;

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
        gripSize = BarTheme.u(SIZE);
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

        // 三条平行斜线，从底边走到右边，端点正好贴着边框内侧。
        // 常态就得到 0.55——太淡的话在深色战场上根本看不出来，用户也就不知道能拖。
        Draw.color(BarTheme.line.r, BarTheme.line.g, BarTheme.line.b,
            (dragging ? 1f : over ? 0.95f : 0.55f) * a);
        Lines.stroke(1f);
        // 三条斜线按边长等比缩放：桌面上还是 5/9/13，手机上跟着放大
        float m = gripSize * 0.19f;
        for(int i = 0; i < 3; i++){
            float d = gripSize * (0.31f + i * 0.25f);
            Lines.line(width - d, m, width - m, d);
        }
        Draw.reset();
    }
}
