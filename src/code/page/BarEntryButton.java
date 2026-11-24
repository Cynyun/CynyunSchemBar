package code;

import arc.Core;
import arc.math.Mathf;
import arc.scene.event.ClickListener;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.game.Schematic;

/**
 * 序列里的一格蓝图按钮。文字只用用户写的标题（未绑定 → “未绑定”，绑定没起名 → “未命名”）。
 *
 * 交互分两态，由面板的编辑开关决定：
 *   普通态  点击 = 用这张蓝图；悬停 0.3 秒 / 长按 0.5 秒 = 弹详情浮层，移开或松手收起
 *   编辑态  点击 = 弹编辑菜单（改标题 / 重新选择 / 上移 / 下移 / 移除）
 *
 * 详情浮层由面板统一管：这里只负责“什么时候该开”，位置和生命周期都在 BarPanel。
 * 悬停判定用 Button 自带的 isOver()（ClickListener 里的字段，O(1)），不做每帧场景树 hit。
 * 不用 enter/exit 累时间：ScrollPane 里子元素的重排/裁剪会让 enter/exit 顺序不可靠，
 * 表现为“要抖一下鼠标才出浮层”。
 */
public class BarEntryButton extends BarButton{

    /** 鼠标停多久出详情。比原版卡片快一点，按钮小、扫过去更频繁。 */
    private static final float HOVER_DELAY = 0.30f;
    /** 手指按住多久算长按。 */
    private static final float LONG_PRESS = 0.5f;
    /** 长按之后这么久内的点击都当“看详情”而不是“用蓝图”。 */
    private static final float LONG_GUARD = 0.8f;

    private final BarPanel owner;
    private final BarSequence sequence;
    private final BarEntry entry;
    /** 解析结果，null = 未绑定或库里已经找不到。 */
    private final Schematic schematic;

    /** 是否已经让面板弹出了自己的浮层（防止每帧重复创建）。 */
    private boolean shown;
    private float hoverTime, pressTime;
    private float longAt = -100f;

    public BarEntryButton(BarPanel owner, BarSequence sequence, BarEntry entry){
        super(entry.displayTitle());
        this.owner = owner;
        this.sequence = sequence;
        this.entry = entry;
        this.schematic = BarLibrary.find(entry.file);
        entry.schematic = schematic;

        ellipsis(true);
        // 无边框样式：一列条目紧贴成一条列表，没有四角括号，靠悬停淡底提示指针位置
        borderless(true);
        onClick(this::clicked);
    }

    @Override
    public void act(float delta){
        super.act(delta);

        if(owner.isEditing()){
            hoverTime = 0f;
            release();
            return;
        }

        // 鼠标悬停：停够时间才弹，扫过去不弹。
        // 指针在按钮上 → 先告诉面板“我现在指着谁”，面板据此保留浮层、避免格子之间来回闪。
        if(isOver() && schematic != null){
            owner.hovering(this);
            hoverTime += delta;
            if(!shown && hoverTime >= HOVER_DELAY){
                shown = true;
                owner.showPreview(this);
            }
        }else{
            hoverTime = 0f;
            release();
        }

        // 触摸长按：直接问按钮自己的 ClickListener“手指此刻是不是按在我身上”。
        // 以前用 enter/exit 记一个 touchOver 标记 + 全局 Core.input.isTouched()：手机上只要漏掉
        // 一次 exit，标记就永久为 true，之后在屏幕任意位置按住 0.5 秒都会弹这个按钮的详情。
        // 现在 pressed 只由“按在本按钮上的 touchDown”置位，touchUp / 被 ScrollPane 取消都会清掉。
        ClickListener clicks = getClickListener();
        if(clicks.isPressed() && schematic != null){
            // 手指在按钮上按住不动才算长按；横向拖动（滚动列表）或滑出按钮就取消
            Tmp.v1.set(Core.input.mouseX(), Core.input.mouseY());
            stageToLocalCoordinates(Tmp.v1);
            boolean still = Mathf.dst(Tmp.v1.x, Tmp.v1.y, clicks.getTouchDownX(), clicks.getTouchDownY())
                <= BarMetrics.sp12();
            if(still){
                pressTime += delta;
                if(pressTime >= LONG_PRESS){
                    pressTime = 0f;
                    longAt = Time.time;
                    shown = true;
                    owner.showPreview(this);
                }
            }else{
                pressTime = 0f;
            }
        }else{
            pressTime = 0f;
        }
    }

    /** 指针离开：浮层不立刻收，交给面板判断（可能正挪到浮层上）。 */
    private void release(){
        if(shown){
            shown = false;
            owner.releasePreview(this);
        }
    }

    private void clicked(){
        // 长按松手后浏览器/引擎还会补一个 click，这一下只当“刚才在看详情”
        if(Time.time - longAt < LONG_GUARD) return;

        owner.entryClicked(sequence, entry, this);
    }

    public BarEntry entry(){
        return entry;
    }

    public BarSequence sequence(){
        return sequence;
    }

    public Schematic schematic(){
        return schematic;
    }
}