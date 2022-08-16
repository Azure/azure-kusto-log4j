package org.apache.logging.log4j.kusto;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MainTest {
    private static final Logger logger = LogManager.getLogger(MainTest.class);

    public static void main(String[] args) {
        for (int i = 0; i < 10000; i++) {
            logger.info("Kusto log info example");
            logger.warn("Kusto log warn example");
            logger.error("Kusto log info example", new RuntimeException("A Random exception"));
            logger.info("Kusto log with parameter {}", "Parameter");
            logger.warn("Kusto log with parameters {} and value {}", "Parameter", 2);
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
