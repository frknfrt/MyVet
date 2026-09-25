package com.vetos.modules.imaging.application.dto;

import java.util.Arrays;

public record ImagingRecordFileContent(String fileName, String contentType, byte[] content) {

    // byte[] varsayilan olarak referansla karsilastirilir/hashlenir (Object.equals) --
    // ayni icerige sahip iki farkli byte[] ornegi (orn. FileStoragePort'tan iki ayri
    // retrieve() cagrisi) yanlislikla esitsiz sayilir. Arrays.equals/hashCode icerigi
    // karsilastirir.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ImagingRecordFileContent(String otherFileName, String otherContentType, byte[] otherContent))) return false;
        return fileName.equals(otherFileName)
            && java.util.Objects.equals(contentType, otherContentType)
            && Arrays.equals(content, otherContent);
    }

    @Override
    public int hashCode() {
        int result = java.util.Objects.hash(fileName, contentType);
        return 31 * result + Arrays.hashCode(content);
    }

    @Override
    public String toString() {
        return "ImagingRecordFileContent[fileName=" + fileName + ", contentType=" + contentType
            + ", content=byte[" + (content == null ? 0 : content.length) + "]]";
    }
}
