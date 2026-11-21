package com.caramelai.support.api;

import com.caramelai.support.api.dto.CreateSessionRequest;
import com.caramelai.support.api.dto.SessionResponse;
import com.caramelai.support.chat.ChatService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final ChatService chat;

    public SessionController(ChatService chat) {
        this.chat = chat;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResponse create(@RequestBody(required = false) CreateSessionRequest request) {
        return chat.createSession(request == null ? null : request.userRef());
    }
}
