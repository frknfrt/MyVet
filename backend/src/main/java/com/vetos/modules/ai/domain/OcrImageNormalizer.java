package com.vetos.modules.ai.domain;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/**
 * Lab/goruntuleme sonuc fotografindan AI ile otomatik doldurma -- REVIZE (2026-10-31,
 * gercek kullanici bulgusu, Task 9 devami). Telefonla CEKILEN ham fotograflar hemen
 * hemen HER ZAMAN bir EXIF "Orientation" etiketiyle gelir: telefonun kamera sensoru
 * fiziksel olarak sabit (genelde yatay) tutulur, dik tutulan fotograflarda piksel
 * verisi YATIK kaydedilir ve goruntuleyici YAZILIMLARIN (Fotograflar uygulamasi,
 * tarayici, isletim sistemi) bu etiketi okuyup EKRANDA dik GOSTERMESI beklenir --
 * baytlarin kendisi donmez. Anthropic'in kendi SSS'si acikca soyle der: "Does Claude
 * read image metadata? No, Claude does not parse or receive any metadata from images
 * passed to it." Yani Claude bu etiketi GORMEZ, goruntuyu HAM (cogu zaman 90/180/270
 * derece yatik) haliyle isler -- kucuk, yatik yazi/sayi icin bir OCR gorevi bu durumda
 * buyuk olasilikla basarisiz/bos sonuc doner. Bir telefon EKRAN GORUNTUSU ise EXIF
 * TASIMAZ (ekranda zaten dik render edilmis pikselleri yakalar) -- bu da "ekran
 * goruntusu okunuyor, dogrudan cekilen foto okunmuyor" bulgusunu tam aciklar.
 *
 * Bu sinif EXIF Orientation etiketini (varsa) OKUYUP donusumu PIKSEL DUZEYINDE
 * UYGULAR -- kullanicidan dosya formati degistirmesini/ekran goruntusu almasini
 * ISTEMEDEN, fotograf oldugu gibi (JPEG) yuklenebilir. Sadece JPEG'de EXIF orientation
 * sorunu olabilecegi icin (ekran goruntuleri PNG/WEBP tasimaz, kamera disi formatlar
 * kapsam disi) sadece JPEG islenir; diger formatlar/orientation=1(normal)/parse
 * basarisiz olursa orijinal baytlar DEGISTIRILMEDEN donulur -- bu fonksiyon asla
 * OCR akisini bloklamaz (en kotu ihtimalle hicbir sey degismemis olur).
 */
public final class OcrImageNormalizer {

    private static final String JPEG_FORMAT_NAME = "jpeg";
    private static final float JPEG_QUALITY = 0.92f;

    private OcrImageNormalizer() {
    }

    /** Format JPEG degilse veya duzeltmeye gerek/imkan yoksa orijinal baytlari degistirmeden doner. */
    public static byte[] normalize(OcrImageFormatDetector.Format format, byte[] content) {
        if (format != OcrImageFormatDetector.Format.JPEG) {
            return content;
        }
        int orientation = readJpegOrientation(content);
        if (orientation <= 1 || orientation > 8) {
            return content;
        }
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(content));
            if (source == null) {
                return content;
            }
            BufferedImage upright = applyOrientation(source, orientation);
            byte[] reEncoded = encodeAsJpeg(upright);
            return reEncoded != null ? reEncoded : content;
        } catch (Exception e) {
            // Donusturme basarisiz olursa (ornegin bozuk/desteklenmeyen bir JPEG varyanti) orijinal
            // baytlarla devam edilir -- en kotu ihtimalle Task 9 oncesi davranisa donulur, OCR akisi
            // bu yuzden KESINTIYE UGRAMAZ.
            return content;
        }
    }

    // --- EXIF Orientation (TIFF tag 0x0112) -- JPEG APP1 "Exif" segmentinden el ile okunur, yeni bagimlilik gerekmez ---

    private static int readJpegOrientation(byte[] data) {
        if (data.length < 4 || (data[0] & 0xFF) != 0xFF || (data[1] & 0xFF) != 0xD8) {
            return 1;
        }
        int i = 2;
        while (i + 4 <= data.length) {
            if ((data[i] & 0xFF) != 0xFF) {
                break;
            }
            int marker = data[i + 1] & 0xFF;
            if (marker == 0xD8 || marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                i += 2;
                continue;
            }
            if (marker == 0xD9 || marker == 0xDA) {
                break; // EOI / SOS -- goruntu verisi basliyor, Exif bundan once gelir
            }
            int segLen = ((data[i + 2] & 0xFF) << 8) | (data[i + 3] & 0xFF);
            if (marker == 0xE1 && segLen >= 8 && i + 4 + 6 <= data.length) {
                int exifStart = i + 4;
                if (data[exifStart] == 'E' && data[exifStart + 1] == 'x' && data[exifStart + 2] == 'i' && data[exifStart + 3] == 'f') {
                    int orientation = readOrientationFromTiff(data, exifStart + 6);
                    if (orientation > 0) {
                        return orientation;
                    }
                }
            }
            i += 2 + segLen;
        }
        return 1;
    }

    private static int readOrientationFromTiff(byte[] data, int tiffStart) {
        if (tiffStart + 8 > data.length) {
            return 0;
        }
        boolean little = data[tiffStart] == 'I' && data[tiffStart + 1] == 'I';
        int ifdOffset = readInt32(data, tiffStart + 4, little);
        int ifdStart = tiffStart + ifdOffset;
        if (ifdStart < 0 || ifdStart + 2 > data.length) {
            return 0;
        }
        int entryCount = readInt16(data, ifdStart, little);
        for (int e = 0; e < entryCount; e++) {
            int entryOffset = ifdStart + 2 + e * 12;
            if (entryOffset + 12 > data.length) {
                break;
            }
            int tag = readInt16(data, entryOffset, little);
            if (tag == 0x0112) {
                return readInt16(data, entryOffset + 8, little);
            }
        }
        return 0;
    }

    private static int readInt16(byte[] d, int off, boolean little) {
        int b0 = d[off] & 0xFF;
        int b1 = d[off + 1] & 0xFF;
        return little ? (b1 << 8 | b0) : (b0 << 8 | b1);
    }

    private static int readInt32(byte[] d, int off, boolean little) {
        int b0 = d[off] & 0xFF;
        int b1 = d[off + 1] & 0xFF;
        int b2 = d[off + 2] & 0xFF;
        int b3 = d[off + 3] & 0xFF;
        return little ? (b3 << 24 | b2 << 16 | b1 << 8 | b0) : (b0 << 24 | b1 << 16 | b2 << 8 | b3);
    }

    // --- Piksel donusumu -- EXIF spesifikasyonundaki 8 orientation degeri icin standart donusum ---

    static BufferedImage applyOrientation(BufferedImage image, int orientation) {
        // REVIZE (2026-10-31): orientation 5 ve 7 ("mirror + rotate") icin TEK bir affine matrisi el ile
        // kurmak hataya cok acik -- bir deneme (case 7) sessizce TAMAMEN SIYAH bir goruntu uretmisti
        // (bkz. OcrImageNormalizerTest tarihcesi/PR notlari). Bunun yerine, ZATEN DOGRULANMIS basit
        // durumlar (mirror=2, rotate90CW=6, rotate90CCW=8) ARDI ARDINA uygulanarak kurulur -- boylece
        // olasi bir hata en kotu ihtimalle "yanlis donus" olur, asla "goruntu kayboldu" olmaz.
        switch (orientation) {
            case 5:
                return applyOrientation(applyOrientation(image, 2), 8);
            case 7:
                return applyOrientation(applyOrientation(image, 2), 6);
            default:
                break;
        }
        int w = image.getWidth();
        int h = image.getHeight();
        AffineTransform t = new AffineTransform();
        switch (orientation) {
            case 2:
                t.concatenate(AffineTransform.getScaleInstance(-1.0, 1.0));
                t.concatenate(AffineTransform.getTranslateInstance(-w, 0));
                break;
            case 3:
                t.concatenate(AffineTransform.getTranslateInstance(w, h));
                t.concatenate(AffineTransform.getQuadrantRotateInstance(2));
                break;
            case 4:
                t.concatenate(AffineTransform.getScaleInstance(1.0, -1.0));
                t.concatenate(AffineTransform.getTranslateInstance(0, -h));
                break;
            case 6:
                t.concatenate(AffineTransform.getTranslateInstance(h, 0));
                t.concatenate(AffineTransform.getQuadrantRotateInstance(1));
                break;
            case 8:
                t.concatenate(AffineTransform.getTranslateInstance(0, w));
                t.concatenate(AffineTransform.getQuadrantRotateInstance(3));
                break;
            default:
                return image;
        }
        boolean swapsDimensions = orientation == 6 || orientation == 8;
        BufferedImage destination = new BufferedImage(
            swapsDimensions ? h : w, swapsDimensions ? w : h, BufferedImage.TYPE_INT_RGB);
        // REVIZE (2026-10-31): AffineTransformOp.filter(src, dst) GERCEK bir JPEG'den decode edilmis
        // BufferedImage ile (tipi genelde TYPE_3BYTE_BGR, bizim TYPE_INT_RGB degil) "ImagingOpException:
        // Unable to transform src image" fırlatıyordu -- sentetik testlerde (src de TYPE_INT_RGB oldugu
        // icin) bu YAKALANAMAMISTI. Graphics2D.drawImage, kaynak renk modelinden BAGIMSIZ calisir.
        Graphics2D g = destination.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(image, t, null);
        g.dispose();
        return destination;
    }

    private static byte[] encodeAsJpeg(BufferedImage image) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(JPEG_FORMAT_NAME);
        if (!writers.hasNext()) {
            return null;
        }
        ImageWriter writer = writers.next();
        try {
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(JPEG_QUALITY);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(out)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(image, null, null), param);
            }
            return out.toByteArray();
        } finally {
            writer.dispose();
        }
    }
}
