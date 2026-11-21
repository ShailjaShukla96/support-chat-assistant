package com.caramelai.support.api;

import com.caramelai.support.api.dto.AssistantReply;
import com.caramelai.support.api.dto.SendMessageRequest;
import com.caramelai.support.chat.ChatService;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sessions/{sessionId}/messages")
public class MessageController {

    private final ChatService chat;

    public MessageController(ChatService chat) {
        this.chat = chat;
    }

    @PostMapping
    public AssistantReply send(@PathVariable UUID sessionId, @RequestBody SendMessageRequest request) {
        return chat.sendMessage(sessionId, request.text());
    }
}
