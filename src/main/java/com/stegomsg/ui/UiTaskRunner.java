package com.stegomsg.ui;

import javafx.concurrent.Task;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

/** Runs blocking database/file operations away from the JavaFX application thread. */
public final class UiTaskRunner {

    private UiTaskRunner() {}

    public static <T> void run(
            Callable<T> work,
            Runnable onStart,
            Consumer<T> onSuccess,
            Consumer<Throwable> onFailure,
            Runnable onFinish) {

        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };

        task.setOnRunning(e -> onStart.run());
        task.setOnSucceeded(e -> {
            try {
                onSuccess.accept(task.getValue());
            } finally {
                onFinish.run();
            }
        });
        task.setOnFailed(e -> {
            try {
                onFailure.accept(task.getException());
            } finally {
                onFinish.run();
            }
        });

        Thread thread = new Thread(task, "stegomsg-ui-task");
        thread.setDaemon(true);
        thread.start();
    }
}
