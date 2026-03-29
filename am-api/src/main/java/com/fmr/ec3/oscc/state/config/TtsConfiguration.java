package com.fmr.ec3.oscc.state.config;

import com.fmr.ec3.oscc.tts.TtsEngine;
import com.fmr.ec3.oscc.tts.cache.InMemoryTtsCache;
import com.fmr.ec3.oscc.tts.cache.TtsCache;
import com.fmr.ec3.oscc.tts.polly.PollyTtsConfig;
import com.fmr.ec3.oscc.tts.polly.PollyTtsEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.polly.PollyClient;

@Configuration
public class TtsConfiguration {

    @Bean
    public PollyClient pollyClient(TtsProperties props) {
        return PollyClient.builder()
                .region(Region.of(props.getAwsRegion()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "tts.cache-enabled", havingValue = "true", matchIfMissing = true)
    public TtsCache ttsCache(TtsProperties props) {
        return new InMemoryTtsCache(props.getCacheMaxEntries());
    }

    @Bean
    public PollyTtsConfig pollyTtsConfig(TtsProperties props,
                                         @Autowired(required = false) TtsCache cache) {
        PollyTtsConfig.Builder builder = PollyTtsConfig.builder()
                .defaultVoiceId(props.getDefaultVoiceId())
                .engine(props.getEngine())
                .sampleRate(props.getSampleRate());
        if (cache != null) {
            builder.cache(cache);
        }
        return builder.build();
    }

    @Bean
    public TtsEngine ttsEngine(PollyClient pollyClient, PollyTtsConfig config) {
        return new PollyTtsEngine(pollyClient, config);
    }
}
