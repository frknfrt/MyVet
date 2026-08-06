package com.vetos.modules.lab.application;

import com.vetos.modules.lab.application.dto.LabResultEvaluation;
import com.vetos.modules.lab.application.dto.LabResultItemInput;
import com.vetos.modules.lab.domain.LabReferenceRangeEvaluator;
import com.vetos.modules.lab.domain.LabValueFlag;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Faz 2 kural tabanli on-degerlendirme -- referans araligi disindaki
 * parametreleri otomatik HIGH/LOW isaretler ve taslak bir "genel
 * degerlendirme" metni uretir. Hicbir kayit degistirmez/persist etmez;
 * hekim onerileri gozden gecirip "Sonucu Kaydet" ile onaylamadan hicbir
 * sey kaydedilmez (@docs/requirements.md "hekim onayi gerektirir" ilkesi).
 */
@Service
public class EvaluateLabResultItemsUseCase {

    public LabResultEvaluation execute(List<LabResultItemInput> items) {
        List<LabResultItemInput> evaluated = new ArrayList<>();
        List<String> abnormalDescriptions = new ArrayList<>();

        for (LabResultItemInput item : items) {
            LabValueFlag computed = LabReferenceRangeEvaluator.evaluate(item.value(), item.referenceRange());
            LabValueFlag resolvedFlag = computed != null ? computed : item.flag();
            evaluated.add(new LabResultItemInput(item.parameterName(), item.value(), item.unit(), item.referenceRange(), resolvedFlag));

            if (computed == LabValueFlag.HIGH || computed == LabValueFlag.LOW) {
                String direction = computed == LabValueFlag.HIGH ? "yuksek" : "dusuk";
                String unitPart = item.unit() != null && !item.unit().isBlank() ? " " + item.unit() : "";
                abnormalDescriptions.add(
                    item.parameterName() + " " + direction + " (" + item.value() + unitPart + ", referans: " + item.referenceRange() + ")"
                );
            }
        }

        String draftSummary = abnormalDescriptions.isEmpty()
            ? "Degerlendirilen parametrelerin tumu referans araliginda."
            : "Referans araligi disinda " + abnormalDescriptions.size() + " parametre tespit edildi: "
                + String.join(", ", abnormalDescriptions)
                + ". (Kural tabanli on-degerlendirme -- kaydetmeden once gozden gecirin.)";

        return new LabResultEvaluation(evaluated, draftSummary);
    }
}
