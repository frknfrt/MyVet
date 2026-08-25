package com.vetos.modules.encounter.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Kural tabanli ilac etkilesim kontrolu -- Ilac Katalogu Yonetimi ekraninda
 * klinik/hekim tarafindan DrugCatalog.interactionFlags alanina girilen
 * ("bu ilac su ilaclarla etkilesir") veriyi, secilen ilaclar arasinda
 * capraz kontrol eder. Gercek bir farmakolojik referans veritabani/API
 * baglanmadigindan hangi ilaclarin etkilestigi burada UYDURULMAZ -- bu
 * sinif sadece onceden isaretlenmis bir eslesme olup olmadigini bulur.
 * LabReferenceRangeEvaluator ile ayni desen (bkz. modules.lab.domain).
 */
public final class DrugInteractionEvaluator {

    private DrugInteractionEvaluator() {}

    public record InteractionWarning(DrugCatalog drugA, DrugCatalog drugB) {}

    public static List<InteractionWarning> evaluate(List<DrugCatalog> drugs) {
        List<InteractionWarning> warnings = new ArrayList<>();
        for (int i = 0; i < drugs.size(); i++) {
            for (int j = i + 1; j < drugs.size(); j++) {
                DrugCatalog a = drugs.get(i);
                DrugCatalog b = drugs.get(j);
                boolean flagged = a.getInteractingDrugIds().contains(b.getId())
                    || b.getInteractingDrugIds().contains(a.getId());
                if (flagged) {
                    warnings.add(new InteractionWarning(a, b));
                }
            }
        }
        return warnings;
    }
}
