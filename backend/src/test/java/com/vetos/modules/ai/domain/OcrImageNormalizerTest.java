package com.vetos.modules.ai.domain;

import org.junit.jupiter.api.Test;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lab/goruntuleme sonuc fotografindan AI ile otomatik doldurma -- REVIZE (2026-10-31, Task 9 devami).
 * EXIF Orientation duzeltmesinin GERCEKTEN dogru piksel donusumu yaptigini dogrular -- bu kod yanlis
 * yazilirsa goruntu DOGRU yerde degil YANLIS bir yonde donmus olur, bu da sorunu "cozmez", baska
 * turlu bozar. 4x2 piksel, her kosesi farkli renkte sentetik bir JPEG ile dogrulanir (gercek bir
 * kamera fotografina ihtiyac yok).
 */
class OcrImageNormalizerTest {

    private static final int RED = 0xFF0000;
    private static final int GREEN = 0x00FF00;
    private static final int BLUE = 0x0000FF;
    private static final int YELLOW = 0xFFFF00;

    /** 4 genislik x 2 yukseklik; sol-ust=RED, sag-ust=GREEN, sol-alt=BLUE, sag-alt=YELLOW. */
    private static BufferedImage markerImage() {
        BufferedImage img = new BufferedImage(4, 2, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 4; x++) {
            img.setRGB(x, 0, x < 2 ? RED : GREEN);
            img.setRGB(x, 1, x < 2 ? BLUE : YELLOW);
        }
        return img;
    }

    private static byte[] toJpegBytes(BufferedImage image) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(1.0f);
        try (MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(out)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), param);
        }
        writer.dispose();
        return out.toByteArray();
    }

    /** JPEG kaynagi tam-sadakatle test etmek icin AffineTransform fonksiyonunu DOGRUDAN cagirir
     *  (JPEG lossy sikistirmanin renk degerlerini hafifce kaydirmasindan bagimsiz, net kose eslesmesi icin). */
    @Test
    void orientation1_isUnchanged() {
        BufferedImage source = markerImage();
        BufferedImage result = OcrImageNormalizer.applyOrientation(source, 1);
        assertThat(result).isSameAs(source);
    }

    @Test
    void orientation3_rotates180_cornersSwapDiagonally() {
        BufferedImage source = markerImage();
        BufferedImage result = OcrImageNormalizer.applyOrientation(source, 3);

        assertThat(result.getWidth()).isEqualTo(4);
        assertThat(result.getHeight()).isEqualTo(2);
        // 180 derece donduruldugunde sol-ust eskiden sag-alt olan YELLOW olmali.
        assertThat(result.getRGB(0, 0) & 0xFFFFFF).isEqualTo(YELLOW);
        assertThat(result.getRGB(3, 1) & 0xFFFFFF).isEqualTo(RED);
    }

    @Test
    void orientation6_rotates90Clockwise_dimensionsSwap() {
        BufferedImage source = markerImage(); // 4x2
        BufferedImage result = OcrImageNormalizer.applyOrientation(source, 6);

        // 90 derece saat yonunde donus genislik/yuksekligi takas eder.
        assertThat(result.getWidth()).isEqualTo(2);
        assertThat(result.getHeight()).isEqualTo(4);
        // Eski sol-ust (RED) 90 CW sonrasi sag-ust'a gelmeli.
        assertThat(result.getRGB(1, 0) & 0xFFFFFF).isEqualTo(RED);
        // Eski sol-alt (BLUE) 90 CW sonrasi sol-ust'a gelmeli.
        assertThat(result.getRGB(0, 0) & 0xFFFFFF).isEqualTo(BLUE);
    }

    @Test
    void orientation8_rotates90CounterClockwise_dimensionsSwap() {
        BufferedImage source = markerImage(); // 4x2
        BufferedImage result = OcrImageNormalizer.applyOrientation(source, 8);

        assertThat(result.getWidth()).isEqualTo(2);
        assertThat(result.getHeight()).isEqualTo(4);
        // Eski sag-ust (GREEN) 90 CCW sonrasi sol-ust'a gelmeli.
        assertThat(result.getRGB(0, 0) & 0xFFFFFF).isEqualTo(GREEN);
    }

    @Test
    void normalize_onJpegWithOrientationTag_rotatesPixelsAndStripsNeedForOrientation() throws Exception {
        // Gercekci ucdan-uca senaryo: orientation=6 (90 derece) etiketli gercek bir JPEG dosyasi
        // uretilir, normalize() hem etiketi okuyup hem pikseli dondurup yeni bir JPEG doner.
        byte[] baseJpeg = toJpegBytes(markerImage());
        byte[] withOrientationTag = insertMinimalExifOrientation(baseJpeg, 6);

        byte[] result = OcrImageNormalizer.normalize(OcrImageFormatDetector.Format.JPEG, withOrientationTag);

        assertThat(result).isNotEqualTo(withOrientationTag);
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(result));
        assertThat(decoded.getWidth()).isEqualTo(2);
        assertThat(decoded.getHeight()).isEqualTo(4);
    }

    @Test
    void normalize_onPngFormat_returnsContentUnchanged() {
        byte[] arbitrary = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12};
        byte[] result = OcrImageNormalizer.normalize(OcrImageFormatDetector.Format.PNG, arbitrary);
        assertThat(result).isSameAs(arbitrary);
    }

    @Test
    void normalize_onCorruptJpegBytes_fallsBackToOriginalWithoutThrowing() {
        byte[] corrupt = insertMinimalExifOrientation(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9}, 6);
        byte[] result = OcrImageNormalizer.normalize(OcrImageFormatDetector.Format.JPEG, corrupt);
        assertThat(result).isEqualTo(corrupt);
    }

    /** Verilen JPEG baytlarinin SOI'sinden (FFD8) hemen sonra, minimal bir APP1/Exif/TIFF/IFD0
     *  segmenti enjekte eder -- sadece Orientation (tag 0x0112) alani olan, little-endian bir TIFF. */
    private static byte[] insertMinimalExifOrientation(byte[] jpeg, int orientationValue) {
        // TIFF header (8) + IFD0: entryCount(2) + 1 entry(12) + nextIfdOffset(4) = 8 + 2 + 12 + 4 = 26 bayt.
        byte[] tiff = new byte[26];
        tiff[0] = 'I'; tiff[1] = 'I'; // little-endian
        tiff[2] = 0x2A; tiff[3] = 0x00; // TIFF magic (42)
        tiff[4] = 0x08; tiff[5] = 0x00; tiff[6] = 0x00; tiff[7] = 0x00; // IFD0 offset = 8
        tiff[8] = 0x01; tiff[9] = 0x00; // entryCount = 1
        // entry: tag(2)=0x0112, type(2)=3(SHORT), count(4)=1, value(4, ilk 2 bayt kullanilir)=orientation
        tiff[10] = 0x12; tiff[11] = 0x01;
        tiff[12] = 0x03; tiff[13] = 0x00;
        tiff[14] = 0x01; tiff[15] = 0x00; tiff[16] = 0x00; tiff[17] = 0x00;
        tiff[18] = (byte) orientationValue; tiff[19] = 0x00; tiff[20] = 0x00; tiff[21] = 0x00;
        tiff[22] = 0x00; tiff[23] = 0x00; tiff[24] = 0x00; tiff[25] = 0x00; // nextIfdOffset = 0

        byte[] exifHeader = {'E', 'x', 'i', 'f', 0, 0};
        int app1DataLen = 2 + exifHeader.length + tiff.length; // segLen alani kendi 2 baytini da sayar
        byte[] app1 = new byte[2 + 2 + exifHeader.length + tiff.length];
        app1[0] = (byte) 0xFF; app1[1] = (byte) 0xE1;
        app1[2] = (byte) ((app1DataLen >> 8) & 0xFF); app1[3] = (byte) (app1DataLen & 0xFF);
        System.arraycopy(exifHeader, 0, app1, 4, exifHeader.length);
        System.arraycopy(tiff, 0, app1, 4 + exifHeader.length, tiff.length);

        byte[] result = new byte[2 + app1.length + (jpeg.length - 2)];
        result[0] = (byte) 0xFF; result[1] = (byte) 0xD8;
        System.arraycopy(app1, 0, result, 2, app1.length);
        System.arraycopy(jpeg, 2, result, 2 + app1.length, jpeg.length - 2);
        return result;
    }
}
