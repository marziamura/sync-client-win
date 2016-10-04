package com.github.tornaia.sync.client.win;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.lang.management.ManagementFactory;

@SpringBootApplication
public class WinClientApp {

    private static final Logger LOG = LoggerFactory.getLogger(WinClientApp.class);

    public static void main(String[] args) {
        LOG.info("PID: " + ManagementFactory.getRuntimeMXBean().getName());
        new SpringApplicationBuilder(WinClientApp.class).web(false).headless(false).run(args);
    }
}