package net.minecraft.viamcp.utils;

import com.viaversion.viaversion.api.platform.PlatformTask;
import com.viaversion.viaversion.api.scheduler.Task;
import com.viaversion.viaversion.api.scheduler.TaskStatus;

public class TaskUtil implements PlatformTask<Void> {
    private final Task task;

    public TaskUtil(final Task task) {
        this.task = task;
    }

    @Override
    public Void getObject() {
        return null;
    }

    @Override
    public void cancel() {
        this.task.cancel();
    }

    public TaskStatus getStatus() {
        return this.task.status();
    }
}
