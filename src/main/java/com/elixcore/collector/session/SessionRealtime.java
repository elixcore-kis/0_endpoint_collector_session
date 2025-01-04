package com.elixcore.collector.session;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SessionRealtime {
    public void start() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        ProcessBuilder processBuilder = new ProcessBuilder("sh", "-c", "ss -E -H -apnmO");
        processBuilder.redirectErrorStream(true);
        // 버퍼 크기를 16KB로 설정
        int bufferSize = 16 * 1024;

        try {
            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()), bufferSize);

            executor.submit(() -> {
                String line;
                try {
                    while ((line = reader.readLine()) != null) {
                        System.out.println(line);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            process.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            executor.shutdown();
        }
    }

    public static void main(String[] args) {
        SessionRealtime au = new SessionRealtime();
        au.start();
        System.out.println("Fork Test");
    }

}
