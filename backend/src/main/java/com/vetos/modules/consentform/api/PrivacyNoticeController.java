package com.vetos.modules.consentform.api;

import com.vetos.modules.consentform.application.PrivacyNoticeUseCase;
import com.vetos.modules.consentform.domain.PrivacyNotice;
import com.vetos.modules.consentform.domain.PrivacyNoticeRepository;
import com.vetos.platform.security.AuthenticatedStaffUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/privacy-notice")
@RequiredArgsConstructor
public class PrivacyNoticeController {
    private final PrivacyNoticeUseCase useCase;
    public record Current(PrivacyNotice notice) {}
    public record Update(@NotBlank @Size(max=20000) String text, @Min(0) long expectedVersion) {}
    public record Delivery(@NotNull UUID ownerId, @NotBlank String hash) {}
    @GetMapping
    @PreAuthorize("hasAnyRole('VET','TECHNICIAN','RECEPTIONIST','ADMIN')")
    public Current current() { return new Current(useCase.current()); }
    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public PrivacyNotice save(@RequestBody @Valid Update input,@AuthenticationPrincipal AuthenticatedStaffUser staff) {
        return useCase.save(input.text(),input.expectedVersion(),staff.staffUserId());
    }
    @PostMapping("/deliveries")
    @PreAuthorize("hasAnyRole('VET','RECEPTIONIST','ADMIN')")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void deliver(@RequestBody @Valid Delivery input,@AuthenticationPrincipal AuthenticatedStaffUser staff,HttpServletRequest request) {
        useCase.recordDelivery(input.ownerId(),input.hash(),staff.staffUserId(),request.getRemoteAddr());
    }
    @GetMapping("/deliveries")
    @PreAuthorize("hasAnyRole('VET','TECHNICIAN','RECEPTIONIST','ADMIN')")
    public List<PrivacyNoticeRepository.Delivery> deliveries(@RequestParam UUID ownerId) { return useCase.deliveries(ownerId); }
}
