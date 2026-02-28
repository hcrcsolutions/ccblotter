package com.fmr.ec3.oscc.audiocapture;

import com.fmr.ec3.oscc.audiocapture.delivery.ObjectStoreDeliveryTarget;
import com.fmr.ec3.oscc.audiocapture.delivery.ObjectStoreUploader;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObjectStoreDeliveryTargetTest {

    @Test
    void delegatesToUploader() throws IOException {
        String[] capturedKey = new String[1];
        byte[][] capturedData = new byte[1][];
        String[] capturedContentType = new String[1];

        ObjectStoreUploader uploader = (key, data, contentType) -> {
            capturedKey[0] = key;
            capturedData[0] = data;
            capturedContentType[0] = contentType;
            return "https://bucket.s3.amazonaws.com/" + key;
        };

        ObjectStoreDeliveryTarget target = new ObjectStoreDeliveryTarget(uploader, "recordings/");
        byte[] wavData = {1, 2, 3};

        String url = target.deliver(wavData, "session-1", "step-1");

        assertThat(capturedKey[0]).isEqualTo("recordings/step-1.wav");
        assertThat(capturedData[0]).isEqualTo(wavData);
        assertThat(capturedContentType[0]).isEqualTo("audio/wav");
        assertThat(url).isEqualTo("https://bucket.s3.amazonaws.com/recordings/step-1.wav");
    }

    @Test
    void correctKeyFormat() throws IOException {
        ObjectStoreUploader uploader = (key, data, contentType) -> key;

        ObjectStoreDeliveryTarget target = new ObjectStoreDeliveryTarget(uploader, "prefix/");
        String url = target.deliver(new byte[]{1}, "session-x", "IVR-001-S3");

        assertThat(url).isEqualTo("prefix/IVR-001-S3.wav");
    }

    @Test
    void propagatesException() {
        ObjectStoreUploader uploader = (key, data, contentType) -> {
            throw new IOException("Upload failed");
        };

        ObjectStoreDeliveryTarget target = new ObjectStoreDeliveryTarget(uploader, "p/");

        assertThatThrownBy(() -> target.deliver(new byte[]{1}, "s1", "step-1"))
                .isInstanceOf(IOException.class)
                .hasMessage("Upload failed");
    }
}
