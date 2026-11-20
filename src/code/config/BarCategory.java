package code;

import arc.struct.Seq;

/**
 * 栏目（设计文档里的 Category）：一组蓝图序列，顶部一排按钮里选一个，切换下面全部序列。
 * 与序列一样，改名不改 {@link #id}。
 */
public class BarCategory{

    public String id;
    public String name;
    public final Seq<BarSequence> sequences = new Seq<>();

    public BarCategory(String id, String name){
        this.id = id;
        this.name = name;
    }
}
