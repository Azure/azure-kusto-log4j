package org.apache.logging.log4j.kusto;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MainTest {

    private static final Logger logger = LogManager.getLogger(MainTest.class);

    public static void main(String[] args) {
        for (int i = 0; i < 10000; i++) {
            logger.info("{} - log4j info example", i);
            logger.warn("{} - log4j warn example", i);
            logger.error("log4j info example", new RuntimeException(i+" - A Random exception"));
            logger.info("log4j with parameter {} and {} ", "Parameter", i);
            logger.warn("log4j with parameters {} and value {}", "Parameter", i);
        }
    }
}
