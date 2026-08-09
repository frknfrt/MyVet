package com.vetos.modules.notification.api;

import com.vetos.modules.notification.api.dto.MessageTemplateRequest;
import com.vetos.modules.notification.api.dto.MessageTemplateResponse;
import com.vetos.modules.notification.application.CreateMessageTemplateUseCase;
import com.vetos.modules.notification.application.DeleteMessageTemplateUseCase;
import com.vetos.modules.notification.application.ListMessageTemplatesUseCase;
import com.vetos.modules.notification.application.UpdateMessageTemplateUseCase;
import com.vetos.modules.notification.application.dto.CreateMessageTemplateCommand;
import com.vetos.modules.notification.application.dto.UpdateMessageTemplateCommand;
import com.vetos.platform.tenancy.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** api-conventions.md rol matrisi: /message-templates/** -> sadece ADMIN. */
@RestController
@RequestMapping("/api/v1/message-templates")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class MessageTemplatesController {

    private final ListMessageTemplatesUseCase listMessageTemplatesUseCase;
    private final CreateMessageTemplateUseCase createMessageTemplateUseCase;
    private final UpdateMessageTemplateUseCase updateMessageTemplateUseCase;
    private final DeleteMessageTemplateUseCase deleteMessageTemplateUseCase;

    @GetMapping
    public List<MessageTemplateResponse> list() {
        return listMessageTemplatesUseCase.execute(TenantContext.current()).stream()
            .map(MessageTemplateResponse::from)
            .toList();
    }

    @PostMapping
    public ResponseEntity<Void> create(@RequestBody @Valid MessageTemplateRequest request) {
        createMessageTemplateUseCase.execute(new CreateMessageTemplateCommand(
            TenantContext.current(), request.name(), request.channel(), request.category(), request.body()
        ));
        return ResponseEntity.status(201).build();
    }

    @PutMapping("/{id}")
    public void update(@PathVariable UUID id, @RequestBody @Valid MessageTemplateRequest request) {
        updateMessageTemplateUseCase.execute(new UpdateMessageTemplateCommand(
            id, request.name(), request.channel(), request.category(), request.body()
        ));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        deleteMessageTemplateUseCase.execute(id);
    }
}
