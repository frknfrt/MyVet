package com.vetos.modules.dataimport.domain;

import java.util.UUID;

/**
 * Geri almayi engelleyen bir bagimlilik: {@code table} tablosunda {@code column} sutunu ile silinecek bir kayda
 * bagli satir. Referans veren tabloda {@code id} sutunu yoksa {@code rowId} null olur.
 */
public record Dependent(String table, UUID rowId, String column) {
}
