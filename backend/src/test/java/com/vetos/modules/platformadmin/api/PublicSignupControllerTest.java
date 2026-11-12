package com.vetos.modules.platformadmin.api;

import com.vetos.modules.platformadmin.application.InitiateSignupCheckoutUseCase;
import com.vetos.modules.platformadmin.application.ListPlansUseCase;
import com.vetos.modules.platformadmin.domain.CheckoutSession;
import com.vetos.modules.tenant.domain.BillingPeriod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PublicSignupControllerTest {
    private final InitiateSignupCheckoutUseCase checkout = mock(InitiateSignupCheckoutUseCase.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(
            new PublicSignupController(mock(ListPlansUseCase.class), checkout)
        ).build();
    }

    @Test
    void forwardsAnnualSelectionFromPublicCheckout() throws Exception {
        when(checkout.execute(anyString(), anyString(), anyString(), isNull(), anyString(), isNull(), eq(BillingPeriod.ANNUAL)))
            .thenReturn(new CheckoutSession("https://example.test/checkout", "token"));
        mvc.perform(post("/api/v1/public/signup/checkout").contentType(MediaType.APPLICATION_JSON).content("""
            {"clinicName":"Klinik","adminFullName":"Ayse","adminEmail":"ayse@example.com",
             "planCode":"PRO","billingPeriod":"ANNUAL","amount":1}
            """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.checkoutFormUrl").value("https://example.test/checkout"));
        verify(checkout).execute("Klinik", "Ayse", "ayse@example.com", null, "PRO", null, BillingPeriod.ANNUAL);
    }

    @Test
    void rejectsUnknownBillingPeriodBeforeStartingPayment() throws Exception {
        mvc.perform(post("/api/v1/public/signup/checkout").contentType(MediaType.APPLICATION_JSON).content("""
            {"clinicName":"Klinik","adminFullName":"Ayse","adminEmail":"ayse@example.com",
             "planCode":"PRO","billingPeriod":"WEEKLY"}
            """))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(checkout);
    }
}
