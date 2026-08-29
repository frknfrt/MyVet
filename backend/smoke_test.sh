#!/usr/bin/env bash
export LC_ALL=C
BASE="http://localhost:8080"
PASS=0
FAIL=0
declare -a FAILLIST

jf() { sed -n "s/.*\"$1\":\"\([^\"]*\)\".*/\1/p" | head -1; }
jnum() { sed -n "s/.*\"$1\":\([0-9][0-9.]*\).*/\1/p" | head -1; }

check() {
  local desc="$1" expected="$2" actual="$3"
  if [ "$expected" == "$actual" ]; then
    PASS=$((PASS+1))
    echo "PASS: $desc ($actual)"
  else
    FAIL=$((FAIL+1))
    FAILLIST+=("$desc (expected [$expected] got [$actual])")
    echo "FAIL: $desc (expected [$expected] got [$actual])"
  fi
}

checkTrue() {
  local desc="$1" cond="$2"
  if [ "$cond" == "1" ]; then
    PASS=$((PASS+1)); echo "PASS: $desc"
  else
    FAIL=$((FAIL+1)); FAILLIST+=("$desc"); echo "FAIL: $desc"
  fi
}

status() { curl -s -o /dev/null -w "%{http_code}" "$@"; }

NOWPLUS() { date -u -d "$1" +%Y-%m-%dT%H:%M:%S; }

# Her calistirmada benzersiz e-posta uretmek icin (ayni Postgres verisine karsi
# tekrar calistirildiginda "email zaten kayitli" cakismasini onler).
RUNID=$(date +%s)

echo "===================================================="
echo "1) KLINIK KAYDI + STAFF OLUSTURMA"
echo "===================================================="
PA_LOGIN=$(curl -s -X POST $BASE/api/v1/platform-admin/auth/login -H "Content-Type: application/json" \
  -d '{"email":"admin@myvet.local","password":"change-me-local-dev-only"}')
PA_TOKEN=$(echo "$PA_LOGIN" | jf token)

curl -s -o /dev/null -X POST $BASE/api/v1/platform-admin/tenants -H "Authorization: Bearer $PA_TOKEN" -H "Content-Type: application/json" \
  -d '{"tenantName":"Smoke Test Klinik","taxNumber":"1112223334","branchName":"Merkez","adminFullName":"Admin Smoke","adminEmail":"smoke-admin-'"$RUNID"'@example.com","adminPassword":"password123"}'

REG=$(curl -s -X POST $BASE/api/v1/auth/login -H "Content-Type: application/json" -d '{"email":"smoke-admin-'"$RUNID"'@example.com","password":"password123"}')
ADMIN_TOKEN=$(echo "$REG" | jf token)
TENANT_ID=$(echo "$REG" | jf tenantId)
BRANCH_ID=$(echo "$REG" | jf branchId)
checkTrue "Klinik kaydi (admin token alindi)" "$([ -n "$ADMIN_TOKEN" ] && echo 1 || echo 0)"

curl -s -o /dev/null -X POST $BASE/api/v1/staff-users -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"branchId":"'"$BRANCH_ID"'","fullName":"Vet Smoke","email":"smoke-vet-'"$RUNID"'@example.com","password":"password123","role":"VET"}'
curl -s -o /dev/null -X POST $BASE/api/v1/staff-users -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"branchId":"'"$BRANCH_ID"'","fullName":"Resepsiyon Smoke","email":"smoke-recep-'"$RUNID"'@example.com","password":"password123","role":"RECEPTIONIST"}'
curl -s -o /dev/null -X POST $BASE/api/v1/staff-users -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"branchId":"'"$BRANCH_ID"'","fullName":"Teknisyen Smoke","email":"smoke-tech-'"$RUNID"'@example.com","password":"password123","role":"TECHNICIAN"}'

VET_LOGIN=$(curl -s -X POST $BASE/api/v1/auth/login -H "Content-Type: application/json" -d '{"email":"smoke-vet-'"$RUNID"'@example.com","password":"password123"}')
VET_TOKEN=$(echo "$VET_LOGIN" | jf token)
VET_ID=$(echo "$VET_LOGIN" | jf staffUserId)
RECEP_TOKEN=$(curl -s -X POST $BASE/api/v1/auth/login -H "Content-Type: application/json" -d '{"email":"smoke-recep-'"$RUNID"'@example.com","password":"password123"}' | jf token)
TECH_TOKEN=$(curl -s -X POST $BASE/api/v1/auth/login -H "Content-Type: application/json" -d '{"email":"smoke-tech-'"$RUNID"'@example.com","password":"password123"}' | jf token)
checkTrue "VET girisi basarili" "$([ -n "$VET_TOKEN" ] && echo 1 || echo 0)"
checkTrue "RECEPTIONIST girisi basarili" "$([ -n "$RECEP_TOKEN" ] && echo 1 || echo 0)"
checkTrue "TECHNICIAN girisi basarili" "$([ -n "$TECH_TOKEN" ] && echo 1 || echo 0)"

echo "===================================================="
echo "2) HASTA / SAHIP MODULU"
echo "===================================================="
SPECIES_ID=$(curl -s -X POST $BASE/api/v1/species -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" -d '{"name":"Kopek-Smoke"}' | jf id)
checkTrue "Tur (species) olusturuldu" "$([ -n "$SPECIES_ID" ] && echo 1 || echo 0)"
BREED_ID=$(curl -s -X POST $BASE/api/v1/species/$SPECIES_ID/breeds -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" -d '{"name":"Kangal-Smoke"}' | jf id)
checkTrue "Irk (breed) olusturuldu" "$([ -n "$BREED_ID" ] && echo 1 || echo 0)"

OWNER_REG=$(curl -s -i -X POST $BASE/api/v1/owners -H "Authorization: Bearer $RECEP_TOKEN" -H "Content-Type: application/json" \
  -d '{"fullName":"Sahip Smoke","phone":"5551112233","marketingConsent":true,"smsConsent":true,"whatsappConsent":true,"notificationConsent":true}')
check "Sahip olusturma HTTP kodu" "201" "$(echo "$OWNER_REG" | head -1 | tr -d '\r' | awk '{print $2}')"
OWNER_ID=$(echo "$OWNER_REG" | jf id)
checkTrue "Sahip id alindi" "$([ -n "$OWNER_ID" ] && echo 1 || echo 0)"

PATIENT_REG=$(curl -s -i -X POST $BASE/api/v1/patients -H "Authorization: Bearer $RECEP_TOKEN" -H "Content-Type: application/json" \
  -d '{"ownerId":"'"$OWNER_ID"'","speciesId":"'"$SPECIES_ID"'","breedId":"'"$BREED_ID"'","name":"Patiii-Smoke","sex":"MALE","aggressive":false}')
check "Hasta olusturma HTTP kodu" "201" "$(echo "$PATIENT_REG" | head -1 | tr -d '\r' | awk '{print $2}')"
PATIENT_ID=$(echo "$PATIENT_REG" | jf id)
checkTrue "Hasta id alindi" "$([ -n "$PATIENT_ID" ] && echo 1 || echo 0)"

echo "===================================================="
echo "3) RANDEVU + BILDIRIM (SMS/WHATSAPP) TETIKLEME"
echo "===================================================="
SERVICE_ID=$(curl -s -X POST $BASE/api/v1/service-types -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Genel Muayene Smoke","defaultDurationMin":30,"defaultPrice":500}' | jf id)
checkTrue "Hizmet tipi olusturuldu" "$([ -n "$SERVICE_ID" ] && echo 1 || echo 0)"

START=$(NOWPLUS "+1 day 10:00")
END=$(NOWPLUS "+1 day 10:30")
APPT_LOC=$(curl -s -i -X POST $BASE/api/v1/appointments -H "Authorization: Bearer $RECEP_TOKEN" -H "Content-Type: application/json" \
  -d '{"patientId":"'"$PATIENT_ID"'","ownerId":"'"$OWNER_ID"'","assignedStaffId":"'"$VET_ID"'","serviceTypeId":"'"$SERVICE_ID"'","scheduledStart":"'"$START"'Z","scheduledEnd":"'"$END"'Z","source":"PHONE"}')
APPT_ID=$(echo "$APPT_LOC" | grep -i '^Location' | sed 's#.*/##' | tr -d '\r')
checkTrue "Randevu olusturuldu" "$([ -n "$APPT_ID" ] && echo 1 || echo 0)"

curl -s -o /dev/null -X POST $BASE/api/v1/appointments/$APPT_ID/confirm -H "Authorization: Bearer $RECEP_TOKEN"
curl -s -o /dev/null -X POST $BASE/api/v1/appointments/$APPT_ID/check-in -H "Authorization: Bearer $RECEP_TOKEN"
S=$(status -X POST $BASE/api/v1/appointments/$APPT_ID/start -H "Authorization: Bearer $VET_TOKEN")
check "Randevu confirm->checkin->start akisi (start)" "200" "$S"

sleep 3
NOTIF_LOGS=$(curl -s "$BASE/api/v1/notifications/logs" -H "Authorization: Bearer $ADMIN_TOKEN")
checkTrue "Bildirim logunda randevu onayi kaydi var" "$(echo "$NOTIF_LOGS" | grep -q "APPOINTMENT_CONFIRMATION" && echo 1 || echo 0)"
NOTIF_STATUS=$(curl -s "$BASE/api/v1/notifications/status" -H "Authorization: Bearer $ADMIN_TOKEN")
echo "  -> notification status: $NOTIF_STATUS"

echo "===================================================="
echo "4) MUAYENE (SOAP) + STOK KULLANIMI"
echo "===================================================="
INV_ITEM_LOC=$(curl -s -i -X POST $BASE/api/v1/inventory-items -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Asi Smoke","category":"Asi","initialQuantity":50,"reorderThreshold":5}')
INV_ITEM_ID=$(echo "$INV_ITEM_LOC" | grep -i '^Location' | sed 's#.*/##' | tr -d '\r')
checkTrue "Stok kalemi olusturuldu" "$([ -n "$INV_ITEM_ID" ] && echo 1 || echo 0)"

ENC_LOC=$(curl -s -i -X POST $BASE/api/v1/encounters -H "Authorization: Bearer $VET_TOKEN" -H "Content-Type: application/json" \
  -d '{"patientId":"'"$PATIENT_ID"'","appointmentId":"'"$APPT_ID"'"}')
ENC_ID=$(echo "$ENC_LOC" | grep -i '^Location' | sed 's#.*/##' | tr -d '\r')
checkTrue "Muayene (encounter) baslatildi" "$([ -n "$ENC_ID" ] && echo 1 || echo 0)"

S=$(status -X PUT $BASE/api/v1/encounters/$ENC_ID/soap -H "Authorization: Bearer $VET_TOKEN" -H "Content-Type: application/json" \
  -d '{"subjective":"Ishtahsizlik","objective":"Ates yok","assessment":"Hafif gastrit","plan":"Diyet + kontrol","aiGenerated":false}')
check "SOAP guncelleme" "200" "$S"

S=$(status -X PUT $BASE/api/v1/encounters/$ENC_ID/vitals -H "Authorization: Bearer $VET_TOKEN" -H "Content-Type: application/json" \
  -d '{"weightKg":22.5,"temperatureC":38.4,"heartRate":90,"respiratoryRate":22}')
check "Vital bulgu guncelleme" "200" "$S"

S=$(status -X POST $BASE/api/v1/encounters/$ENC_ID/materials -H "Authorization: Bearer $VET_TOKEN" -H "Content-Type: application/json" \
  -d '{"inventoryItemId":"'"$INV_ITEM_ID"'","quantity":2}')
check "Kullanilan malzeme kaydi" "201" "$S"

S=$(status -X POST $BASE/api/v1/encounters/$ENC_ID/finalize -H "Authorization: Bearer $VET_TOKEN")
check "Muayene finalize (otomatik fatura+stok tetikler)" "200" "$S"

sleep 1
STOCK_AFTER=$(curl -s "$BASE/api/v1/inventory-items" -H "Authorization: Bearer $ADMIN_TOKEN" | grep -o '"id":"'"$INV_ITEM_ID"'"[^}]*"quantityOnHand":[0-9]*' | grep -oE '"quantityOnHand":[0-9]*' | jnum quantityOnHand)
check "Stok finalize sonrasi 50->48 dustu (otomatik dusum)" "48" "$STOCK_AFTER"

echo "===================================================="
echo "5) FATURALAMA (otomatik taslak + manuel fatura + e-Fatura)"
echo "===================================================="
INVOICES=$(curl -s "$BASE/api/v1/invoices" -H "Authorization: Bearer $ADMIN_TOKEN")
AUTO_INV_ID=$(echo "$INVOICES" | grep -o '"id":"[^"]*","ownerId":"'"$OWNER_ID"'"' | jf id)
checkTrue "Encounter finalize sonrasi otomatik DRAFT fatura acildi" "$([ -n "$AUTO_INV_ID" ] && echo 1 || echo 0)"

curl -s -o /dev/null -X POST $BASE/api/v1/invoices/$AUTO_INV_ID/lines -H "Authorization: Bearer $RECEP_TOKEN" -H "Content-Type: application/json" \
  -d '{"description":"Muayene ucreti","quantity":1,"unitPrice":500,"discountAmount":0,"vatRate":20}'
S=$(status -X POST $BASE/api/v1/invoices/$AUTO_INV_ID/issue -H "Authorization: Bearer $RECEP_TOKEN")
check "Otomatik fatura kesildi (issue)" "200" "$S"
S=$(status -X POST $BASE/api/v1/invoices/$AUTO_INV_ID/payments -H "Authorization: Bearer $RECEP_TOKEN" -H "Content-Type: application/json" \
  -d '{"method":"CASH","amount":600}')
check "Odeme kaydedildi" "200" "$S"

# Manuel fatura (bu turun ozelligi)
MANUAL_LOC=$(curl -s -i -X POST $BASE/api/v1/invoices -H "Authorization: Bearer $RECEP_TOKEN" -H "Content-Type: application/json" -d '{"ownerId":"'"$OWNER_ID"'"}')
MANUAL_INV_ID=$(echo "$MANUAL_LOC" | grep -i '^Location' | sed 's#.*/##' | tr -d '\r')
checkTrue "Manuel fatura (POST /invoices) olusturuldu" "$([ -n "$MANUAL_INV_ID" ] && echo 1 || echo 0)"
curl -s -o /dev/null -X POST $BASE/api/v1/invoices/$MANUAL_INV_ID/lines -H "Authorization: Bearer $RECEP_TOKEN" -H "Content-Type: application/json" \
  -d '{"description":"Mama satisi","quantity":2,"unitPrice":150,"discountAmount":0,"vatRate":10}'
S=$(status -X POST $BASE/api/v1/invoices/$MANUAL_INV_ID/issue -H "Authorization: Bearer $RECEP_TOKEN")
check "Manuel fatura kesildi" "200" "$S"

sleep 3
EFATURA_SUBS=$(curl -s "$BASE/api/v1/efatura/submissions" -H "Authorization: Bearer $ADMIN_TOKEN")
checkTrue "e-Fatura: otomatik faturaya karsilik gonderim kaydi var" "$(echo "$EFATURA_SUBS" | grep -q "\"invoiceId\":\"$AUTO_INV_ID\"" && echo 1 || echo 0)"
checkTrue "e-Fatura: manuel faturaya karsilik gonderim kaydi var" "$(echo "$EFATURA_SUBS" | grep -q "\"invoiceId\":\"$MANUAL_INV_ID\"" && echo 1 || echo 0)"
EFATURA_STATUS=$(curl -s "$BASE/api/v1/efatura/status" -H "Authorization: Bearer $ADMIN_TOKEN")
echo "  -> efatura status: $EFATURA_STATUS"

echo "===================================================="
echo "6) KASA YONETIMI + BORC LISTESI"
echo "===================================================="
CASH_LOC=$(curl -s -i -X POST $BASE/api/v1/cash-register/open -H "Authorization: Bearer $RECEP_TOKEN" -H "Content-Type: application/json" -d '{"openingBalance":1000,"notes":"smoke"}')
CASH_ID=$(echo "$CASH_LOC" | grep -i '^Location' | sed 's#.*/##' | tr -d '\r')
checkTrue "Kasa acildi" "$([ -n "$CASH_ID" ] && echo 1 || echo 0)"
S=$(status -X POST $BASE/api/v1/cash-register/$CASH_ID/close -H "Authorization: Bearer $RECEP_TOKEN" -H "Content-Type: application/json" -d '{"closingBalance":1600,"notes":"smoke close"}')
check "Kasa kapatildi" "200" "$S"
BALANCES=$(curl -s "$BASE/api/v1/owner-balances" -H "Authorization: Bearer $ADMIN_TOKEN")
checkTrue "Borc listesi endpoint calisiyor" "$(echo "$BALANCES" | grep -q "ownerId" && echo 1 || echo 0)"

echo "===================================================="
echo "7) TARBIL ENTEGRASYONU (asi + kimliklendirme sync)"
echo "===================================================="
VACC_LOC=$(curl -s -i -X POST $BASE/api/v1/vaccination-records -H "Authorization: Bearer $VET_TOKEN" -H "Content-Type: application/json" \
  -d '{"patientId":"'"$PATIENT_ID"'","vaccineName":"Kuduz Asisi Smoke","administeredDate":"'"$(date -u +%Y-%m-%d)"'","status":"ADMINISTERED"}')
VACC_ID=$(echo "$VACC_LOC" | grep -i '^Location' | sed 's#.*/##' | tr -d '\r')
checkTrue "Asi kaydi olusturuldu" "$([ -n "$VACC_ID" ] && echo 1 || echo 0)"

S=$(status -X PUT $BASE/api/v1/patients/$PATIENT_ID/identification -H "Authorization: Bearer $VET_TOKEN" -H "Content-Type: application/json" \
  -d '{"microchipNumber":"999000111222333","tarbilAnimalId":"TR-SMOKE-001"}')
check "Hasta kimliklendirme (mikrocip/TARBIL no) guncellendi" "200" "$S"

sleep 4
TARBIL_LOGS=$(curl -s "$BASE/api/v1/tarbil/sync-logs" -H "Authorization: Bearer $ADMIN_TOKEN")
TARBIL_COUNT_FOR_PATIENT=$(echo "$TARBIL_LOGS" | grep -o "\"patientId\":\"$PATIENT_ID\"" | wc -l)
checkTrue "TARBIL: hasta icin en az 2 sync log kaydi olustu (asi+kimlik)" "$([ "$TARBIL_COUNT_FOR_PATIENT" -ge 2 ] && echo 1 || echo 0)"
TARBIL_STATUS=$(curl -s "$BASE/api/v1/tarbil/status" -H "Authorization: Bearer $ADMIN_TOKEN")
echo "  -> tarbil status: $TARBIL_STATUS"
TARBIL_PENDING_FOR_PATIENT=$(echo "$TARBIL_LOGS" | grep -o "\"patientId\":\"$PATIENT_ID\"[^}]*}" | grep -c '"status":"PENDING"')
checkTrue "TARBIL: hicbir kayit sonsuza dek PENDING kalmadi" "$([ "$TARBIL_PENDING_FOR_PATIENT" -eq 0 ] && echo 1 || echo 0)"

echo "===================================================="
echo "8) LABORATUVAR (kural tabanli on-degerlendirme)"
echo "===================================================="
LAB_ID=$(curl -s -X POST $BASE/api/v1/lab-results -H "Authorization: Bearer $VET_TOKEN" -H "Content-Type: application/json" \
  -d '{"patientId":"'"$PATIENT_ID"'","testName":"Tam Kan Sayimi Smoke"}' | jf id)
checkTrue "Lab tahlil istegi olusturuldu" "$([ -n "$LAB_ID" ] && echo 1 || echo 0)"
EVAL=$(curl -s -X POST $BASE/api/v1/lab-results/evaluate -H "Authorization: Bearer $VET_TOKEN" -H "Content-Type: application/json" \
  -d '{"items":[{"parameterName":"WBC","value":"25","unit":"10^9/L","referenceRange":"6-17"}]}')
checkTrue "Lab kural-tabanli on-degerlendirme HIGH isaretledi" "$(echo "$EVAL" | grep -q '\"flag\":\"HIGH\"' && echo 1 || echo 0)"
S=$(status -X PUT $BASE/api/v1/lab-results/$LAB_ID/complete -H "Authorization: Bearer $VET_TOKEN" -H "Content-Type: application/json" \
  -d '{"resultSummary":"Lokosit yuksek","items":[{"parameterName":"WBC","value":"25","unit":"10^9/L","referenceRange":"6-17","flag":"HIGH"}]}')
check "Lab sonucu tamamlandi" "200" "$S"

echo "===================================================="
echo "9) GORUNTULEME"
echo "===================================================="
IMG_ID=$(curl -s -X POST $BASE/api/v1/imaging-records -H "Authorization: Bearer $VET_TOKEN" -H "Content-Type: application/json" \
  -d '{"patientId":"'"$PATIENT_ID"'","modality":"XRAY","bodyRegion":"Toraks"}' | jf id)
checkTrue "Goruntuleme kaydi olusturuldu" "$([ -n "$IMG_ID" ] && echo 1 || echo 0)"
S=$(status -X PUT $BASE/api/v1/imaging-records/$IMG_ID/complete -H "Authorization: Bearer $VET_TOKEN" -H "Content-Type: application/json" \
  -d '{"findings":"Anomali yok"}')
check "Goruntuleme bulgusu girildi" "200" "$S"

echo "===================================================="
echo "10) YATIS (BOARDING) + OTOMATIK FATURA"
echo "===================================================="
ROOM_LOC=$(curl -s -i -X POST $BASE/api/v1/boarding-rooms -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"groupName":"Kedi Smoke","name":"K1-Smoke","capacity":1,"dailyRate":300}')
ROOM_ID=$(echo "$ROOM_LOC" | grep -i '^Location' | sed 's#.*/##' | tr -d '\r')
checkTrue "Konaklama odasi olusturuldu" "$([ -n "$ROOM_ID" ] && echo 1 || echo 0)"
CHECKIN=$(date -u +%Y-%m-%d)
CHECKOUT=$(date -u -d "+2 day" +%Y-%m-%d)
STAY_LOC=$(curl -s -i -X POST $BASE/api/v1/boarding-stays -H "Authorization: Bearer $RECEP_TOKEN" -H "Content-Type: application/json" \
  -d '{"roomId":"'"$ROOM_ID"'","patientId":"'"$PATIENT_ID"'","checkInDate":"'"$CHECKIN"'","expectedCheckOutDate":"'"$CHECKOUT"'"}')
STAY_ID=$(echo "$STAY_LOC" | grep -i '^Location' | sed 's#.*/##' | tr -d '\r')
checkTrue "Konaklama kaydi olusturuldu" "$([ -n "$STAY_ID" ] && echo 1 || echo 0)"
BOARDING_INV=$(curl -s "$BASE/api/v1/invoices/by-boarding-stay/$STAY_ID" -H "Authorization: Bearer $ADMIN_TOKEN" | jf id)
checkTrue "Konaklama icin otomatik fatura acildi" "$([ -n "$BOARDING_INV" ] && echo 1 || echo 0)"

echo "===================================================="
echo "11) RECETE + ILAC ETKILESIM KONTROLU"
echo "===================================================="
DRUG_A=$(curl -s -X POST $BASE/api/v1/drugs -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Amoksisilin-Smoke","activeIngredient":"Amoxicillin","isControlled":false}' | jf id)
DRUG_B=$(curl -s -X POST $BASE/api/v1/drugs -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Karprofen-Smoke","activeIngredient":"Carprofen","isControlled":false}' | jf id)
checkTrue "Ilac A olusturuldu" "$([ -n "$DRUG_A" ] && echo 1 || echo 0)"
checkTrue "Ilac B olusturuldu" "$([ -n "$DRUG_B" ] && echo 1 || echo 0)"
S=$(status -X PUT $BASE/api/v1/drugs/$DRUG_A -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Amoksisilin-Smoke","activeIngredient":"Amoxicillin","isControlled":false,"interactingDrugIds":["'"$DRUG_B"'"]}')
check "Ilac etkilesim iliskisi kaydedildi" "200" "$S"
INTERACT=$(curl -s -X POST $BASE/api/v1/drugs/check-interactions -H "Authorization: Bearer $VET_TOKEN" -H "Content-Type: application/json" \
  -d '{"drugIds":["'"$DRUG_A"'","'"$DRUG_B"'"]}')
checkTrue "Ilac etkilesim kontrolu uyari dondurdu" "$(echo "$INTERACT" | grep -q "drugAId" && echo 1 || echo 0)"

RX_LOC=$(curl -s -i -X POST $BASE/api/v1/prescriptions -H "Authorization: Bearer $VET_TOKEN" -H "Content-Type: application/json" \
  -d '{"patientId":"'"$PATIENT_ID"'","encounterId":"'"$ENC_ID"'","controlledSubstance":false,"items":[{"drugId":"'"$DRUG_A"'","dosage":"1 tablet","frequency":"gunde 2","durationDays":7,"route":"ORAL"}]}')
RX_ID=$(echo "$RX_LOC" | grep -i '^Location' | sed 's#.*/##' | tr -d '\r')
checkTrue "Recete olusturuldu" "$([ -n "$RX_ID" ] && echo 1 || echo 0)"

echo "===================================================="
echo "12) RAPORLAMA"
echo "===================================================="
S=$(status "$BASE/api/v1/invoices/reports/revenue" -H "Authorization: Bearer $ADMIN_TOKEN")
check "Ciro raporu" "200" "$S"
S=$(status "$BASE/api/v1/invoices/reports/staff-performance" -H "Authorization: Bearer $ADMIN_TOKEN")
check "Hekim performans raporu" "200" "$S"
S=$(status "$BASE/api/v1/invoices/reports/branch-comparison" -H "Authorization: Bearer $ADMIN_TOKEN")
check "Sube karsilastirma raporu" "200" "$S"
S=$(status "$BASE/api/v1/invoices/reports/revenue/export" -H "Authorization: Bearer $ADMIN_TOKEN")
check "Ciro raporu CSV export" "200" "$S"

echo "===================================================="
echo "13) SMS/WHATSAPP KAMPANYA + SABLONLAR"
echo "===================================================="
TPL_STATUS=$(status -X POST $BASE/api/v1/message-templates -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Smoke Sablon","channel":"SMS","category":"GENEL","body":"Merhaba {musteri_adi}"}')
check "Mesaj sablonu olusturuldu" "201" "$TPL_STATUS"
TEMPLATES=$(curl -s "$BASE/api/v1/message-templates" -H "Authorization: Bearer $ADMIN_TOKEN")
checkTrue "Sablon listesi doluyor" "$(echo "$TEMPLATES" | grep -q "Smoke Sablon" && echo 1 || echo 0)"

CANDIDATES=$(curl -s "$BASE/api/v1/owners/campaign-candidates" -H "Authorization: Bearer $ADMIN_TOKEN")
checkTrue "Kampanya alici adaylari (sahip) listesi calisiyor" "$(echo "$CANDIDATES" | grep -q "$OWNER_ID" && echo 1 || echo 0)"

CAMPAIGN_RESULT=$(curl -s -X POST $BASE/api/v1/notifications/campaigns/send -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"channel":"SMS","messageBody":"Smoke test kampanya","recipients":[{"ownerId":"'"$OWNER_ID"'","phone":"5551112233","smsConsent":true,"whatsappConsent":true,"variables":{},"label":null}]}')
QUEUED=$(echo "$CAMPAIGN_RESULT" | jnum queued)
check "Kampanya gonderimi kuyruklandi" "1" "$QUEUED"

echo "===================================================="
echo "14) DAVET (INVITE) AKISI"
echo "===================================================="
INV_LOC=$(curl -s -i -X POST $BASE/api/v1/staff-invites -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"branchId":"'"$BRANCH_ID"'","fullName":"Davetli Smoke","email":"smoke-davetli-'"$RUNID"'@example.com","role":"VET"}')
check "Davet olusturma" "201" "$(echo "$INV_LOC" | head -1 | tr -d '\r' | awk '{print $2}')"
INVITE_TOKEN=$(grep "Davet e-postasi" /tmp/backend_full_test2.log | tail -1 | sed -E 's#.*link=http://localhost:5173/davet/##')
checkTrue "Mock davet e-postasi loglandi (token alindi)" "$([ -n "$INVITE_TOKEN" ] && echo 1 || echo 0)"
S=$(status "$BASE/api/v1/public/staff-invites/$INVITE_TOKEN")
check "Public davet bilgisi erisilebilir" "200" "$S"
S=$(status -X POST $BASE/api/v1/public/staff-invites/$INVITE_TOKEN/accept -H "Content-Type: application/json" -d '{"password":"invitepass123"}')
check "Davet kabul edildi" "200" "$S"
NEWSTAFF_LOGIN=$(curl -s -X POST $BASE/api/v1/auth/login -H "Content-Type: application/json" -d '{"email":"smoke-davetli-'"$RUNID"'@example.com","password":"invitepass123"}')
checkTrue "Davetli hesapla giris yapilabildi" "$(echo "$NEWSTAFF_LOGIN" | grep -q '\"role\":\"VET\"' && echo 1 || echo 0)"

echo "===================================================="
echo "15) ROL/YETKI SINIRLARI (403 beklenen senaryolar)"
echo "===================================================="
S=$(status "$BASE/api/v1/invoices" -H "Authorization: Bearer $VET_TOKEN")
check "VET faturalara erisemiyor" "403" "$S"
S=$(status -X POST $BASE/api/v1/staff-invites -H "Authorization: Bearer $RECEP_TOKEN" -H "Content-Type: application/json" -d '{"branchId":"'"$BRANCH_ID"'","fullName":"x","email":"blocked@example.com","role":"VET"}')
check "RECEPTIONIST davet gonderemiyor (ADMIN-only)" "403" "$S"
S=$(status -X POST $BASE/api/v1/encounters -H "Authorization: Bearer $TECH_TOKEN" -H "Content-Type: application/json" -d '{"patientId":"'"$PATIENT_ID"'"}')
check "TECHNICIAN muayene olusturamiyor (VET/ADMIN-only)" "403" "$S"
S=$(status -X POST $BASE/api/v1/patients -H "Authorization: Bearer $TECH_TOKEN" -H "Content-Type: application/json" -d '{"ownerId":"'"$OWNER_ID"'","speciesId":"'"$SPECIES_ID"'","name":"x"}')
check "TECHNICIAN hasta yazma yapamiyor" "403" "$S"
S=$(status "$BASE/api/v1/invoices" -H "Authorization: Bearer $ADMIN_TOKEN")
check "ADMIN faturalara erisebiliyor" "200" "$S"

echo "===================================================="
echo "16) PLATFORM ADMIN PANELI (paralel auth yigini)"
echo "===================================================="
PA_LOGIN=$(curl -s -X POST $BASE/api/v1/platform-admin/auth/login -H "Content-Type: application/json" \
  -d '{"email":"admin@myvet.local","password":"change-me-local-dev-only"}')
PA_TOKEN=$(echo "$PA_LOGIN" | jf token)
checkTrue "Platform admin girisi basarili" "$([ -n "$PA_TOKEN" ] && echo 1 || echo 0)"
PA_TENANTS=$(curl -s "$BASE/api/v1/platform-admin/tenants" -H "Authorization: Bearer $PA_TOKEN")
checkTrue "Platform admin tenant listesinde smoke klinik gorunuyor" "$(echo "$PA_TENANTS" | grep -q "Smoke Test Klinik" && echo 1 || echo 0)"
S=$(status "$BASE/api/v1/platform-admin/tenants" -H "Authorization: Bearer $ADMIN_TOKEN")
checkTrue "Normal klinik ADMIN token'i ile platform-admin'e erisilemiyor (401/403)" "$([ "$S" == "401" ] || [ "$S" == "403" ] && echo 1 || echo 0)"

echo "===================================================="
echo "17) PUBLIC WEBSITE / ONLINE RANDEVU WIDGET'I"
echo "===================================================="
S=$(status "$BASE/api/v1/public/clinics/$BRANCH_ID")
check "Public klinik bilgisi (kimliksiz)" "200" "$S"
PUB_OWNER=$(curl -s -X POST $BASE/api/v1/public/owners -H "Content-Type: application/json" \
  -d '{"tenantId":"'"$TENANT_ID"'","fullName":"Widget Sahip Smoke","phone":"5559998877"}')
PUB_OWNER_ID=$(echo "$PUB_OWNER" | jf id)
checkTrue "Public sahip kaydi (widget)" "$([ -n "$PUB_OWNER_ID" ] && echo 1 || echo 0)"
PUB_PATIENT=$(curl -s -X POST $BASE/api/v1/public/patients -H "Content-Type: application/json" \
  -d '{"ownerId":"'"$PUB_OWNER_ID"'","speciesId":"'"$SPECIES_ID"'","name":"Widget Pati Smoke"}')
PUB_PATIENT_ID=$(echo "$PUB_PATIENT" | jf id)
checkTrue "Public hasta kaydi (widget)" "$([ -n "$PUB_PATIENT_ID" ] && echo 1 || echo 0)"
WSTART=$(NOWPLUS "+3 day 11:00")
WEND=$(NOWPLUS "+3 day 11:30")
WIDGET_APPT_ID=$(curl -s -X POST $BASE/api/v1/public/appointment-requests -H "Content-Type: application/json" \
  -d '{"tenantId":"'"$TENANT_ID"'","branchId":"'"$BRANCH_ID"'","patientId":"'"$PUB_PATIENT_ID"'","ownerId":"'"$PUB_OWNER_ID"'","serviceTypeId":"'"$SERVICE_ID"'","scheduledStart":"'"$WSTART"'Z","scheduledEnd":"'"$WEND"'Z"}' \
  -D /tmp/widget_hdrs.txt -o /dev/null; grep -i '^Location' /tmp/widget_hdrs.txt | sed 's#.*/##' | tr -d '\r')
checkTrue "Online randevu widget talebi olusturuldu" "$([ -n "$WIDGET_APPT_ID" ] && echo 1 || echo 0)"
WIDGET_APPT=$(curl -s "$BASE/api/v1/appointments?weekStart=$(date -u +%Y-%m-%d)" -H "Authorization: Bearer $ADMIN_TOKEN")
checkTrue "Widget randevusu REQUESTED olarak sisteme dustu" "$(echo "$WIDGET_APPT" | grep -q '\"source\":\"WIDGET\"' && echo 1 || echo 0)"

echo ""
echo "===================================================="
echo "SONUC: $PASS basarili, $FAIL basarisiz"
echo "===================================================="
if [ "$FAIL" -gt 0 ]; then
  echo "Basarisiz senaryolar:"
  for f in "${FAILLIST[@]}"; do echo "  - $f"; done
fi
