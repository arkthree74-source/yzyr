package com.dsh.toolbox.data;

/**
 * 同步进度：谁在同步就调谁，界面只负责把它画出来。
 *
 * <p>先 {@link #plan(int)} 报备总步数，再每完成一门课 {@link #step(String)} 推一次；
 * 界面据此画出确定进度的进度条。没有界面监听时（后台同步）这些调用什么也不做。</p>
 */
public final class SyncProgress {

    public interface Listener {
        /**
         * @param done  已完成步数
         * @param total 总步数（可能随后续 plan 增加）
         * @param label 当前正在做的事，例如「雨课堂 · 数字电子技术」
         */
        void onProgress(int done, int total, String label);
    }

    private final Listener listener;
    private int total;
    private int done;

    public SyncProgress(Listener listener) {
        this.listener = listener;
    }

    /** 报备还要做多少步（可多次累加，比如每个平台加载课程列表时各加 1）。 */
    public synchronized void plan(int steps) {
        if (steps <= 0) return;
        total += steps;
        emit("准备中…");
    }

    /** 完成一步。 */
    public synchronized void step(String label) {
        done++;
        if (done > total) total = done;
        emit(label);
    }

    /** 只更新文案，不推进进度。 */
    public synchronized void note(String label) {
        emit(label);
    }

    public synchronized int done() {
        return done;
    }

    public synchronized int total() {
        return total;
    }

    private void emit(String label) {
        if (listener != null) listener.onProgress(done, total, label);
    }
}