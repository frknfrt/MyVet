package com.vetos.modules.platformadmin.domain;

/** 6 haneli dogrulama kodu ureticisi (bastaki sifirlar korunur). Testlerde sabitlenebilsin diye port. */
public interface TrialOtpGenerator {
    String next();
}
