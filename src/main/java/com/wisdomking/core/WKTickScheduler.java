package com.wisdomking.core;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.function.BooleanSupplier;

/**
 * 1tickあたりの処理量を制限しながら遅延タスクを実行するスケジューラ。
 *
 * <p>土木AI（道路敷設・整地・堀の掘削）や国家シミュレーションは大量のブロック操作を
 * 伴うため、サーバーtickの予算内で分割実行する必要がある。本クラスは
 * 「1tickあたり最大 {@code budget} 件」という制約でジョブを消化する。</p>
 */
public final class WKTickScheduler {
	private final Deque<ScheduledTask> queue = new ArrayDeque<>();

	/** 遅延実行タスク。{@code step} が true を返すと完了。 */
	public interface Task {
		/** @return 完了したら true */
		boolean step();
	}

	private record ScheduledTask(Task task, int delay) {
	}

	/** 次tick以降に実行するタスクを登録する。 */
	public void schedule(int delayTicks, Task task) {
		queue.addLast(new ScheduledTask(task, Math.max(0, delayTicks)));
	}

	/** 即座に実行するタスクを登録する。 */
	public void schedule(Task task) {
		schedule(1, task);
	}

	/**
	 * 予算内でタスクを消化する。
	 *
	 * @param budget このtickで実行する最大タスクステップ数
	 * @return 消化したステップ数
	 */
	public int tick(int budget) {
		int used = 0;
		Iterator<ScheduledTask> it = queue.iterator();
		while (it.hasNext() && used < budget) {
			ScheduledTask st = it.next();
			if (st.delay() > 0) {
				it.remove();
				queue.addLast(new ScheduledTask(st.task(), st.delay() - 1));
				continue;
			}
			used++;
			boolean done;
			try {
				done = st.task().step();
			} catch (Throwable t) {
				WKLog.error("scheduled task failed", t);
				done = true;
			}
			if (done) {
				it.remove();
			}
		}
		return used;
	}

	public int size() {
		return queue.size();
	}

	public void clear() {
		queue.clear();
	}

	/** 条件が満たされるまで待機するタスクを生成するヘルパ。 */
	public static Task await(BooleanSupplier condition, Runnable action) {
		return () -> {
			if (!condition.getAsBoolean()) {
				return false;
			}
			action.run();
			return true;
		};
	}
}
