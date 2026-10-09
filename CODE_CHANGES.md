# Code Changes Log

This document tracks all code changes committed and pushed to the repository.

---

## Addition-1: Channel-Specific Authorization

**Date:** 2026-10-09  
**Branch:** `docs1`  
**Commit:** `c06ad5f`  
**PR:** [#New PR](https://github.com/anilkr09/DiscordClone/pull/new/docs1)

### Summary
Implemented channel-specific authorization to ensure users can only access channels they are members of (server channels) or participants in (DM/Group DM channels).

---

### New Files Created

| File | Description |
|------|-------------|
| `src/main/java/com/discordclone/security/ChannelAuthorizationService.java` | Core authorization service that checks user access to channels based on channel type (server, DM, group DM) |
| `src/main/java/com/discordclone/exception/ChannelAccessDeniedException.java` | Custom exception thrown when a user lacks permission to access a channel |

---

### Modified Files

| File | Changes |
|------|---------|
| `src/main/java/com/discordclone/controller/MessageController.java` | Added authorization checks to all endpoints:<br>• `GET /api/messages/channels/{channelId}` - verify channel access before returning messages<br>• `POST /chat.send` (WebSocket) - verify channel access before sending messages<br>• `PUT /api/messages/{messageId}` - verify message ownership + channel access before editing<br>• `DELETE /api/messages/{messageId}` - verify message ownership + channel access before deleting<br>• Added `@CurrentUser UserPrincipal currentUser` parameter to all REST endpoints |
| `src/main/java/com/discordclone/service/MessageService.java` | Added `getMessageById(String messageId)` method to support message ownership verification |
| `src/main/java/com/discordclone/exception/GlobalExceptionHandler.java` | Added handler for `ChannelAccessDeniedException` → returns **403 FORBIDDEN** |
| `src/main/java/com/discordclone/exception/WebSocketExceptionHandler.java` | Added handler for `ChannelAccessDeniedException` → sends error to `/user/queue/errors` |

---

### Authorization Logic

| Channel Type | Access Check |
|--------------|--------------|
| **Server Text/Voice** | User must exist in `server_members` table for that server (`MemberRepository.existsByIdUserIdAndIdServerId`) |
| **Direct Message (DM)** | User ID must be in `dmKey` (format: `userId1_userId2`, sorted) |
| **Group DM** | User ID must be in `dmKey` (format: `groupdm_userId1_userId2_userId3...`) |

---

### API Response Examples

**Successful Access (200 OK):**
```json
{
  "content": [...],
  "pageable": {...},
  "totalElements": 50,
  ...
}
```

**Access Denied (403 FORBIDDEN):**
```json
{
  "status": 403,
  "error": "FORBIDDEN",
  "message": "You don't have permission to access this channel",
  "path": "/api/messages/channels/999"
}
```

**WebSocket Access Denied:** Error sent to `/user/queue/errors`:
```
Channel Access Denied: You don't have permission to access this channel
```

---

### Testing

```bash
# Test unauthorized access to a channel
curl -H "Authorization: Bearer <token>" \
  http://localhost:8080/api/messages/channels/999
# Expected: 403 FORBIDDEN
```

---

### Files Changed Summary

- **7 files changed**
- **1940 insertions(+), 11 deletions(-)**
- **2 new files created**

---

*Next entry: Addition-2*