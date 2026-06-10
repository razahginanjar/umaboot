package io.umaboot.intellij;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;

final class PreviewMergeState {

    private static final Key<PreviewMergeState> KEY = Key.create("umaboot.previewMergeState");

    private int nextToken = 1;
    private int activeToken = 0;
    private int activeRemaining = 0;

    static PreviewMergeState get(Project project) {
        PreviewMergeState state = project.getUserData(KEY);
        if (state == null) {
            state = new PreviewMergeState();
            project.putUserData(KEY, state);
        }
        return state;
    }

    synchronized int start(int changeCount) {
        activeToken = nextToken++;
        activeRemaining = changeCount;
        return activeToken;
    }

    synchronized boolean isActive(int token) {
        return activeToken == token;
    }

    synchronized boolean hasActiveSession() {
        return activeToken != 0;
    }

    synchronized void complete(int token) {
        if (activeToken != token) return;
        activeRemaining--;
        if (activeRemaining <= 0) {
            activeToken = 0;
            activeRemaining = 0;
        }
    }

    synchronized int reset() {
        int count = activeRemaining;
        activeToken = 0;
        activeRemaining = 0;
        return count;
    }
}
