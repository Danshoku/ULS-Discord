package server;

import java.io.*;
import java.net.*;
import java.io.File;

public class VerificationTest {
    public static void main(String[] args) {
        try {
            // 1. Reset Database
            File dbFile = new File("discord.db");
            if (dbFile.exists()) {
                dbFile.delete();
                System.out.println("[TEST] Deleted old database.");
            }

            // 2. Start Server
            new Thread(() -> {
                try {
                    Server.main(new String[] {});
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();

            Thread.sleep(2000); // Wait for server startup

            // 3. Client A connects
            System.out.println("[TEST] Connecting Client A (Alice)...");
            Socket socketA = new Socket("127.0.0.1", 1234);
            PrintWriter outA = new PrintWriter(socketA.getOutputStream(), true);
            BufferedReader inA = new BufferedReader(new InputStreamReader(socketA.getInputStream()));

            // Login A
            outA.println("Alice");
            outA.println("passwordA");

            String response = inA.readLine();
            System.out.println("[A RECV] " + response);
            if (response != null && !response.equals("SUCCESS")) {
                // consume potential FAIL message
            }

            // 4. Create Server
            System.out.println("[TEST] Client A creating server 'TestServer'...");
            outA.println("/create_server TestServer");

            // Expect JOINED_SERVER
            boolean joined = false;
            long start = System.currentTimeMillis();
            while (System.currentTimeMillis() - start < 3000) {
                if (inA.ready()) {
                    String line = inA.readLine();
                    System.out.println("[A RECV] " + line);
                    if (line.startsWith("JOINED_SERVER:TestServer"))
                        joined = true;
                }
                Thread.sleep(50);
            }
            if (!joined)
                System.err.println("[FAIL] Did not receive JOINED_SERVER for Creator");

            // 5. Create Channel
            System.out.println("[TEST] Creating channel '#offtopic'...");
            outA.println("/create_channel TestServer #offtopic");
            Thread.sleep(500);

            // 6. Send Message
            System.out.println("[TEST] Client A sending message...");
            outA.println("TestServer|#general|Hello from Alice");

            // Expect MSG back
            boolean received = false;
            start = System.currentTimeMillis();
            while (System.currentTimeMillis() - start < 3000) {
                if (inA.ready()) {
                    String line = inA.readLine();
                    System.out.println("[A RECV] " + line);
                    if (line.contains("MSG:TestServer|#general") && line.contains("Hello from Alice")) {
                        received = true;
                        break; // Found it
                    }
                }
                Thread.sleep(50);
            }
            if (!received)
                System.err.println("[FAIL] Client A did not receive its own message.");
            else
                System.out.println("[PASS] Client A received its own message.");

            // 7. Client B connects
            System.out.println("[TEST] Connecting Client B (Bob)...");
            Socket socketB = new Socket("127.0.0.1", 1234);
            PrintWriter outB = new PrintWriter(socketB.getOutputStream(), true);
            BufferedReader inB = new BufferedReader(new InputStreamReader(socketB.getInputStream()));

            outB.println("Bob");
            outB.println("passwordB");
            inB.readLine(); // SUCCESS

            // Check if B can spam
            System.out.println("[TEST] Client B (Non-member) sending message...");
            outB.println("TestServer|#general|Ghost message");

            // Check A for ghost message
            boolean ghostReceived = false;
            start = System.currentTimeMillis();
            while (System.currentTimeMillis() - start < 2000) {
                if (inA.ready()) {
                    String line = inA.readLine();
                    System.out.println("[A RECV] " + line);
                    if (line.contains("Ghost message"))
                        ghostReceived = true;
                }
                Thread.sleep(50);
            }

            if (ghostReceived)
                System.err.println("[FAIL] Security Issue: Non-member B sent message to Server!");
            else
                System.out.println("[PASS] Non-member message prevented (or not received by A).");

            // 8. B Joins
            System.out.println("[TEST] Client B joining server...");
            outB.println("/join TestServer");
            Thread.sleep(500);

            System.out.println("[TEST] Client B (Member) sending message...");
            outB.println("TestServer|#general|Hello I am Bob");

            boolean bobReceived = false;
            start = System.currentTimeMillis();
            while (System.currentTimeMillis() - start < 3000) {
                if (inA.ready()) {
                    String line = inA.readLine();
                    System.out.println("[A RECV] " + line);
                    if (line.contains("Hello I am Bob")) {
                        bobReceived = true;
                        break;
                    }
                }
                Thread.sleep(50);
            }

            if (bobReceived)
                System.out.println("[PASS] Member B message received.");
            else
                System.err.println("[FAIL] Member B message NOT received.");

            socketA.close();
            socketB.close();

            System.exit(0);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
