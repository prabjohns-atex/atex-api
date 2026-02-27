package com.atex.desk.api.onecms;

import com.atex.desk.api.dto.ContentWriteDto;
import com.atex.desk.api.dto.WorkspaceInfoDto;
import com.atex.onecms.content.ContentVersionId;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory workspace storage for transient draft content.
 * Workspaces are used by the preview pipeline (reporter-tool / onecms-lib)
 * to store draft versions before save or discard.
 */
@Component
public class WorkspaceStorage {

    private final ConcurrentHashMap<String, WorkspaceData> workspaces = new ConcurrentHashMap<>();

    public record DraftEntry(ContentWriteDto contentWrite, String contentIdString,
                             ContentVersionId draftVersionId, Instant createdAt) {}

    private record WorkspaceData(ConcurrentHashMap<String, DraftEntry> drafts, Instant createdAt) {}

    public void createWorkspace(String wsId) {
        workspaces.putIfAbsent(wsId, new WorkspaceData(new ConcurrentHashMap<>(), Instant.now()));
    }

    public boolean workspaceExists(String wsId) {
        return workspaces.containsKey(wsId);
    }

    public void putDraft(String wsId, String contentIdStr, DraftEntry entry) {
        workspaces.computeIfAbsent(wsId, k -> new WorkspaceData(new ConcurrentHashMap<>(), Instant.now()))
                  .drafts().put(contentIdStr, entry);
    }

    public DraftEntry getDraft(String wsId, String contentIdStr) {
        WorkspaceData ws = workspaces.get(wsId);
        if (ws == null) return null;
        return ws.drafts().get(contentIdStr);
    }

    public boolean removeDraft(String wsId, String contentIdStr) {
        WorkspaceData ws = workspaces.get(wsId);
        if (ws == null) return false;
        return ws.drafts().remove(contentIdStr) != null;
    }

    public int clearWorkspace(String wsId) {
        WorkspaceData ws = workspaces.get(wsId);
        if (ws == null) return 0;
        int count = ws.drafts().size();
        ws.drafts().clear();
        return count;
    }

    public boolean deleteWorkspace(String wsId) {
        return workspaces.remove(wsId) != null;
    }

    public WorkspaceInfoDto getWorkspaceInfo(String wsId) {
        WorkspaceData ws = workspaces.get(wsId);
        if (ws == null) return null;
        return new WorkspaceInfoDto(wsId, ws.drafts().size(), ws.createdAt());
    }

    public Collection<DraftEntry> getAllDrafts(String wsId) {
        WorkspaceData ws = workspaces.get(wsId);
        if (ws == null) return null;
        return ws.drafts().values();
    }
}
