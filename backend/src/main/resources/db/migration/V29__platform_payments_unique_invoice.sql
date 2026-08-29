-- Ayni fatura icin es zamanli iki callback (cift tiklama / tarayici retry) yarisi
-- uygulama katmanindaki status kontrolunu (HandlePaymentCallbackUseCase) atlatabilir:
-- ikisi de ISSUED okur, ikisi de platform_payments satiri ekler. Benzersizlik
-- kisiti bunu DB katmaninda fiziksel olarak imkansiz kilar.
ALTER TABLE platform_payments ADD CONSTRAINT uq_platform_payments_invoice_id UNIQUE (invoice_id);
