package com.vetos.modules.consentform.domain;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Imzali formun SHA-256 ozeti: snapshot \n values \n signer \n metadata \n imza PNG baytlari. */
public final class SignedFormDigest {
    private SignedFormDigest() {}

    public static String compute(String templateSnapshot, String valuesJson, String signerJson, String metadataJson,
                                 byte[] signaturePng) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (String part : new String[] {templateSnapshot, valuesJson, signerJson, metadataJson}) {
            out.writeBytes(part.getBytes(StandardCharsets.UTF_8));
            out.write('\n');
        }
        out.writeBytes(signaturePng);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(out.toByteArray()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
