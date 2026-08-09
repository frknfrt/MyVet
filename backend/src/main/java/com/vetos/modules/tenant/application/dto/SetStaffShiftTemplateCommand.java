package com.vetos.modules.tenant.application.dto;

import java.util.List;
import java.util.UUID;

public record SetStaffShiftTemplateCommand(UUID staffUserId, List<StaffShiftEntry> entries) {}
