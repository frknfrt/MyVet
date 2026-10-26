package com.vetos.platform.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Stok miktarı (spec 2026-10-25): en az 0,01, en çok 100.000, en çok 2 ondalık. TARBİL Miktar kutusu 0,01 altını sessizce
 * 0,01 yapar ve 2 haneye yuvarlar; Vetly fazla haneyi yuvarlamak yerine reddeder ki iki taraf ayrışmasın. null serbest.
 */
@Documented
@Constraint(validatedBy = {})
@ReportAsSingleViolation
@DecimalMin("0.01")
@DecimalMax("100000")
@Digits(integer = 6, fraction = 2)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.METHOD, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface StockAmount {
    String message() default "Miktar en az 0,01, en fazla 100.000 ve en çok 2 ondalık olmalı";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
