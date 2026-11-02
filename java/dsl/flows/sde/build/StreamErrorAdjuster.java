// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.build;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public record StreamErrorAdjuster(BufferedReader stream, Consumer<String> listener) implements Runnable {
    public static void waitWhileCheckingForOOM(Process process) throws InterruptedException, IOException {
        while (!process.waitFor(100, TimeUnit.MILLISECONDS)) {
            try (BufferedReader br = new BufferedReader(new FileReader("/proc/meminfo"))) {
                String line;
                Long availableMemory = null;
                while ((line = br.readLine()) != null) {
                    if (line.startsWith("MemAvailable:") && line.endsWith("kB")) {
                        availableMemory = Long.parseLong(line.substring(13, line.length() - 2).strip());
                    }
                }
                if (availableMemory == null || availableMemory < 4000000) {
                    new ProcessBuilder("kill", "--", "-" + process.pid()).start().waitFor();
                    throw new RuntimeException("Available memory has been exhausted");
                }
            }
        }
    }

    @Override
    public void run() {
        String line;
        try {
            while ((line = stream.readLine()) != null) {
                if (line.startsWith("ERROR: ") && line.endsWith("]")) {
                    int bracketIndex = line.lastIndexOf('[');
                    if (bracketIndex != -1) {
                        int colonIndex = line.indexOf(':', bracketIndex + 1);
                        if (colonIndex != -1) {
                            listener.accept(line.substring(6, bracketIndex).trim());
                            listener.accept("\tat (" + line.substring(bracketIndex + 1, line.length() - 1) + ")");
                            continue;
                        }
                    }
                }
                listener.accept(line);
            }
        } catch (IOException notUsed) {
        }
    }
}
