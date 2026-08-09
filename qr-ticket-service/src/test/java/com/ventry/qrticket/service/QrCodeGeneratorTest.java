package com.ventry.qrticket.service;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class QrCodeGeneratorTest {

    private final QrCodeGenerator qrCodeGenerator = new QrCodeGenerator();

    @Test
    void generate_roundTripsBackToOriginalContent() throws Exception {
        String content = "{\"bookingId\":\"b-1\",\"eventId\":\"e-1\",\"tierId\":\"t-1\",\"customerId\":\"c-1\"}";

        byte[] png = qrCodeGenerator.generate(content, 200);

        assertThat(decode(png)).isEqualTo(content);
    }

    @Test
    void generate_producesAValidPngImage() throws Exception {
        byte[] png = qrCodeGenerator.generate("hello", 100);

        assertThat(png).isNotEmpty();
        assertThat(ImageIO.read(new ByteArrayInputStream(png))).isNotNull();
    }

    /**
     * Decodes with ZXing's own reader, not just asserting bytes are non-empty - a mistake in
     * how generate() builds the BitMatrix/PNG could still produce a "valid-looking" image that
     * doesn't actually decode back to the right content.
     */
    private String decode(byte[] png) throws Exception {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
        Result result = new MultiFormatReader().decode(bitmap);
        return result.getText();
    }
}
