package com.vetos.modules.notification.application.dto;

import com.vetos.modules.notification.domain.TemplateChannel;

import java.util.UUID;

public record UpdateMessageTemplateCommand(UUID templateId, String name, TemplateChannel channel, String category, String body) {}
