package com.vetos;

import com.vetos.modules.consentform.application.*;
import com.vetos.modules.consentform.application.dto.*;
import com.vetos.modules.consentform.domain.*;
import com.vetos.modules.patient.domain.*;
import com.vetos.modules.tenant.domain.*;
import com.vetos.platform.security.JwtTokenProvider;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PrivacyNoticeIntegrationTest extends TenantScopedTestSupport {
    @Autowired PrivacyNoticeUseCase notices;
    @Autowired PrivacyNoticeRepository noticeRepository;
    @Autowired OwnerRepository owners;
    @Autowired TenantRepository tenants;
    @Autowired FormTemplateRepository templates;
    @Autowired CreateSignedFormUseCase create;
    @Autowired GetSignedFormUseCase getForm;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider jwt;
    @MockitoBean PatientLookupPort patients;
    @MockitoBean StaffUserLookupPort staffLookup;
    UUID tenantA,tenantB,ownerA,ownerB;
    final UUID staff=UUID.randomUUID(),patient=UUID.randomUUID();
    final byte[] png={(byte)0x89,0x50,0x4e,0x47,13,10,26,10,1,2};
    @BeforeEach void setup() {
        tenantA=inRootSession(() -> tenants.save(Tenant.register("A Klinik",null)).getId());
        tenantB=inRootSession(() -> tenants.save(Tenant.register("B Klinik",null)).getId());
        ownerA=asTenant(tenantA,() -> owners.save(Owner.register(tenantA,"Ayşe","05321112233","a@example.test",null)).getId());
        ownerB=asTenant(tenantB,() -> owners.save(Owner.register(tenantB,"Başka Sahip","05321112233",null,null)).getId());
        when(patients.findSummaryById(patient)).thenReturn(new PatientSummary(patient,"Pamuk",ownerA,"Kedi"));
        when(staffLookup.findSummaryById(staff)).thenReturn(new StaffSummary(staff,"Test Hekim",StaffRole.VET,null));
    }
    @AfterEach void cleanup() {
        for(UUID t:List.of(tenantA,tenantB)) {
            var refs=jdbc.queryForList("SELECT signature_storage_ref FROM signed_forms WHERE tenant_id=?",String.class,t);
            jdbc.update("DELETE FROM signed_form_submissions WHERE tenant_id=?",t);
            jdbc.update("DELETE FROM signed_forms WHERE tenant_id=?",t);
            for(var ref:refs) jdbc.update("DELETE FROM stored_files WHERE id=?",UUID.fromString(ref));
            jdbc.update("DELETE FROM form_templates WHERE tenant_id=?",t);
            jdbc.update("DELETE FROM privacy_notice_deliveries WHERE tenant_id=?",t);
            jdbc.update("DELETE FROM clinic_privacy_notices WHERE tenant_id=?",t);
            jdbc.update("DELETE FROM owners WHERE tenant_id=?",t);
            jdbc.update("DELETE FROM tenants WHERE id=?",t);
        }
    }
    String bearer(UUID tenant,String role) {return "Bearer "+jwt.generateToken(staff,tenant,List.of(),role);}
    @Test void commonTextIsClinicSpecific_andCustomVersionsAreIsolated() {
        var a=asTenant(tenantA,() -> notices.current());var b=asTenant(tenantB,() -> notices.current());
        assertThat(a.text()).contains("A Klinik");assertThat(b.text()).contains("B Klinik");assertThat(a.hash()).isNotEqualTo(b.hash());
        var updated=asTenant(tenantA,() -> notices.save("A Kliniğin onaylı metni",1,staff));
        assertThat(updated.version()).isEqualTo(2);
        assertThat(asTenant(tenantB,() -> notices.current()).hash()).isEqualTo(b.hash());
        assertThatThrownBy(() -> asTenant(tenantA,() -> notices.save("Eski sürümden güncelleme",1,staff)))
            .hasMessageContaining("metni değişti");
        assertThat(asTenant(tenantA,() -> notices.current()).text()).isEqualTo(updated.text());
    }
    @Test void deliveryRecordsStaffStatementAndExactText_withoutCreatingExplicitConsent() throws Exception {
        var old=asTenant(tenantA,() -> notices.current());
        mvc.perform(post("/api/v1/privacy-notice/deliveries").header("Authorization",bearer(tenantA,"RECEPTIONIST"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"ownerId\":\""+ownerA+"\",\"hash\":\""+old.hash()+"\"}"))
            .andExpect(status().isNoContent());
        asTenant(tenantA,() -> notices.save("Yeni metin",1,staff));
        var history=asTenant(tenantA,() -> notices.deliveries(ownerA));
        assertThat(history).hasSize(1);assertThat(history.get(0).text()).isEqualTo(old.text());
        assertThat(history.get(0).recordedBy()).isEqualTo(staff);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM consent_records WHERE owner_id=?",Long.class,ownerA)).isZero();
        mvc.perform(get("/api/v1/privacy-notice/deliveries").param("ownerId",ownerA.toString())
            .header("Authorization",bearer(tenantB,"ADMIN"))).andExpect(status().isNotFound());
    }
    @Test void onlyAdministratorCanChangeText_andForeignOwnerCannotReceiveDelivery() throws Exception {
        mvc.perform(put("/api/v1/privacy-notice").header("Authorization",bearer(tenantA,"RECEPTIONIST"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"Yeni\",\"expectedVersion\":1}"))
            .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/privacy-notice").header("Authorization",bearer(tenantA,"ADMIN"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"Yeni\",\"expectedVersion\":1}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
        mvc.perform(post("/api/v1/privacy-notice/deliveries").header("Authorization",bearer(tenantA,"ADMIN"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"ownerId\":\""+ownerB+"\",\"hash\":\"x\"}"))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/privacy-notice")).andExpect(status().isUnauthorized());
    }
    CreateSignedFormCommand command(boolean enabled) {
        return asTenant(tenantA,() -> {
            var content=new FormTemplateContent("Tedavi",List.of(new FormSection(null,List.of(
                new FormItem(FormItemType.SIGNATURE,"imza","İmza",null,true,null,null,null,false)))),null,enabled);
            var template=templates.save(FormTemplate.create(tenantA,"Tedavi",FormTemplateContentCodec.toJson(content),null,staff));
            var notice=notices.current();
            return new CreateSignedFormCommand(template.getId(),patient,null,Map.of(),new SignerInput(SignerType.OWNER,null,null,null),
                Base64.getEncoder().encodeToString(png),"127.0.0.1","Test",template.contentHash(),UUID.randomUUID(),
                enabled,enabled?notice.hash():null,enabled,false,enabled);
        });
    }
    @Test void signedSnapshotSurvivesNewVersion_retryIsIdempotent_andTamperingIsDetected() {
        var cmd=command(true);
        UUID id=asTenant(tenantA,() -> create.execute(staff,cmd));
        var detail=asTenant(tenantA,() -> getForm.execute(id));
        assertThat(detail.integrityOk()).isTrue();assertThat(detail.content().privacyNotice().version()).isEqualTo(1);
        var owner=asTenant(tenantA,() -> owners.findById(ownerA).orElseThrow());
        assertThat(owner.isSmsConsent()).isTrue();assertThat(owner.isWhatsappConsent()).isFalse();
        assertThat(owner.isEmailNotificationConsent()).isTrue();assertThat(owner.isMarketingConsent()).isFalse();
        asTenant(tenantA,() -> notices.save("Yeni metin",1,staff));
        assertThat(asTenant(tenantA,() -> create.execute(staff,cmd))).isEqualTo(id);
        assertThat(asTenant(tenantA,() -> getForm.execute(id)).content().privacyNotice().text()).isEqualTo(detail.content().privacyNotice().text());
        String raw=jdbc.queryForObject("SELECT template_snapshot FROM signed_forms WHERE id=?",String.class,id);
        jdbc.update("UPDATE signed_forms SET template_snapshot=? WHERE id=?",raw.replace("A Klinik","Değiştirilen Klinik"),id);
        assertThat(asTenant(tenantA,() -> getForm.execute(id)).integrityOk()).isFalse();
    }
    @Test void optionalSectionDisabled_preservesExistingOwnerPreferencesAndLegacyContent() {
        var cmd=command(false);
        UUID id=asTenant(tenantA,() -> create.execute(staff,cmd));
        var d=asTenant(tenantA,() -> getForm.execute(id));
        assertThat(d.integrityOk()).isTrue();assertThat(d.content().privacyNotice()).isNull();
        var owner=asTenant(tenantA,() -> owners.findById(ownerA).orElseThrow());
        assertThat(owner.isWhatsappConsent()).isTrue();assertThat(owner.isEmailNotificationConsent()).isFalse();
    }
}
