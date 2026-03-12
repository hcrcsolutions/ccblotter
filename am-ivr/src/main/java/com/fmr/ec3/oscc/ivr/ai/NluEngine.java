package com.fmr.ec3.oscc.ivr.ai;

import java.util.List;

/**
 * Interface for natural language understanding engines.
 */
public interface NluEngine {

    /**
     * Classifies the input text against expected intents.
     *
     * @param text            the text to classify
     * @param expectedIntents the list of expected intent names
     * @return the classification result with confidence and entities
     */
    NluResult classify(String text, List<String> expectedIntents);
}
