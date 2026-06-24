package com.example;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.text.format.Formatter;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    // --- Tab Navigation Variables ---
    private Button btnTabServer, btnTabClient, btnTabTools;
    private ScrollView layoutServer, layoutClient, layoutTools;
    private TextView tvGlobalStatus;

    // --- Server-Side Variables ---
    private EditText etServerPort;
    private TextView tvServerIpInfo, tvServerConsole;
    private Button btnStartServer, btnStopServer;
    private LinearLayout cardServerActions;
    private EditText etBroadcastMsg;
    private Button btnSendBroadcast, btnHostTrivia;
    private ScrollView scrollServerConsole;

    private ServerSocket serverSocket;
    private Thread serverThread;
    private boolean isServerRunning = false;
    private final List<ClientHandler> connectedClients = Collections.synchronizedList(new ArrayList<>());
    private final StringBuilder serverLogBuilder = new StringBuilder();

    // Trivia Game Variables (Hosted by Server)
    private boolean isTriviaRunning = false;
    private int currentTriviaIndex = -1;
    private final Map<String, Integer> playerScores = new HashMap<>(); // Client IP/Name -> Score
    private final Map<String, String> currentRoundAnswers = new HashMap<>(); // Client IP/Name -> Selected Choice

    private final String[][] triviaQuestions = {
        {"What is the default local network port for Minecraft servers?", "25565", "19132", "7777", "8080", "A"},
        {"Which transport protocol is faster for real-time local multiplayer gaming?", "TCP", "UDP", "HTTP", "FTP", "B"},
        {"What does LAN stand for in multiplayer gaming?", "Local Area Network", "Loop Access Node", "Lightweight App Node", "Local Action Net", "A"},
        {"Which IP range is commonly used for local home routers and servers?", "192.168.x.x", "8.8.8.x", "104.24.x.x", "224.0.0.x", "A"},
        {"What tool wakes up a local PC server from standby over network?", "Ping", "Port Scanner", "Wake-on-LAN", "DHCP", "C"}
    };

    // --- Client-Side Variables ---
    private EditText etClientIp, etClientPort, etClientName;
    private Button btnConnectServer, btnDisconnectServer;
    private LinearLayout layoutTriviaPane;
    private TextView tvTriviaQuestion, tvTriviaHeader;
    private Button btnOptA, btnOptB, btnOptC, btnOptD;
    private TextView tvClientChat;
    private LinearLayout layoutClientChatInput;
    private EditText etClientChatMsg;
    private Button btnSendChat;
    private ScrollView scrollClientChat;

    private Socket clientSocket;
    private PrintWriter clientWriter;
    private Thread clientRxThread;
    private boolean isClientConnected = false;
    private final StringBuilder clientChatBuilder = new StringBuilder();

    // --- Diagnostics & Tools Variables ---
    private TextView tvToolsNetworkDetails;
    private EditText etPingIp, etScanTargetIp, etWolMac;
    private Button btnPingTest, btnStartScan, btnSendWol;
    private TextView tvPingResult, tvScanResults, tvWolStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Bind all XML views
        initViews();

        // Setup Tab Navigation
        setupNavigation();

        // Setup Server actions
        setupServerControls();

        // Setup Client actions
        setupClientControls();

        // Setup Utility Tools actions
        setupToolsControls();

        // Query default local network info
        updateLocalNetworkInfo();
    }

    private void initViews() {
        // Tabs
        btnTabServer = findViewById(R.id.btn_tab_server);
        btnTabClient = findViewById(R.id.btn_tab_client);
        btnTabTools = findViewById(R.id.btn_tab_tools);
        layoutServer = findViewById(R.id.layout_server);
        layoutClient = findViewById(R.id.layout_client);
        layoutTools = findViewById(R.id.layout_tools);
        tvGlobalStatus = findViewById(R.id.tv_global_status);

        // Server View
        etServerPort = findViewById(R.id.et_server_port);
        tvServerIpInfo = findViewById(R.id.tv_server_ip_info);
        tvServerConsole = findViewById(R.id.tv_server_console);
        btnStartServer = findViewById(R.id.btn_start_server);
        btnStopServer = findViewById(R.id.btn_stop_server);
        cardServerActions = findViewById(R.id.card_server_actions);
        etBroadcastMsg = findViewById(R.id.et_broadcast_msg);
        btnSendBroadcast = findViewById(R.id.btn_send_broadcast);
        btnHostTrivia = findViewById(R.id.btn_host_trivia);
        scrollServerConsole = findViewById(R.id.scroll_server_console);

        // Client View
        etClientIp = findViewById(R.id.et_client_ip);
        etClientPort = findViewById(R.id.et_client_port);
        etClientName = findViewById(R.id.et_client_name);
        btnConnectServer = findViewById(R.id.btn_connect_server);
        btnDisconnectServer = findViewById(R.id.btn_disconnect_server);
        layoutTriviaPane = findViewById(R.id.layout_trivia_pane);
        tvTriviaHeader = findViewById(R.id.tv_trivia_header);
        tvTriviaQuestion = findViewById(R.id.tv_trivia_question);
        btnOptA = findViewById(R.id.btn_opt_a);
        btnOptB = findViewById(R.id.btn_opt_b);
        btnOptC = findViewById(R.id.btn_opt_c);
        btnOptD = findViewById(R.id.btn_opt_d);
        tvClientChat = findViewById(R.id.tv_client_chat);
        layoutClientChatInput = findViewById(R.id.layout_client_chat_input);
        etClientChatMsg = findViewById(R.id.et_client_chat_msg);
        btnSendChat = findViewById(R.id.btn_send_chat);
        scrollClientChat = findViewById(R.id.scroll_client_chat);

        // Net Tools View
        tvToolsNetworkDetails = findViewById(R.id.tv_tools_network_details);
        etPingIp = findViewById(R.id.et_ping_ip);
        btnPingTest = findViewById(R.id.btn_ping_test);
        tvPingResult = findViewById(R.id.tv_ping_result);
        etScanTargetIp = findViewById(R.id.et_scan_target_ip);
        btnStartScan = findViewById(R.id.btn_start_scan);
        tvScanResults = findViewById(R.id.tv_scan_results);
        etWolMac = findViewById(R.id.et_wol_mac);
        btnSendWol = findViewById(R.id.btn_send_wol);
        tvWolStatus = findViewById(R.id.tv_wol_status);
    }

    // ==========================================
    // TAB NAVIGATION
    // ==========================================
    private void setupNavigation() {
        btnTabServer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTab(0);
            }
        });

        btnTabClient.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTab(1);
            }
        });

        btnTabTools.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTab(2);
                updateLocalNetworkInfo();
            }
        });
    }

    private void switchTab(int index) {
        // Reset buttons background and colors
        btnTabServer.setBackgroundResource(R.drawable.tab_unselected_bg);
        btnTabServer.setTextColor(getResources().getColor(R.color.text_primary));

        btnTabClient.setBackgroundResource(R.drawable.tab_unselected_bg);
        btnTabClient.setTextColor(getResources().getColor(R.color.text_primary));

        btnTabTools.setBackgroundResource(R.drawable.tab_unselected_bg);
        btnTabTools.setTextColor(getResources().getColor(R.color.text_primary));

        // Hide all views
        layoutServer.setVisibility(View.GONE);
        layoutClient.setVisibility(View.GONE);
        layoutTools.setVisibility(View.GONE);

        if (index == 0) {
            btnTabServer.setBackgroundResource(R.drawable.tab_selected_bg);
            btnTabServer.setTextColor(getResources().getColor(R.color.white));
            layoutServer.setVisibility(View.VISIBLE);
        } else if (index == 1) {
            btnTabClient.setBackgroundResource(R.drawable.tab_selected_bg);
            btnTabClient.setTextColor(getResources().getColor(R.color.white));
            layoutClient.setVisibility(View.VISIBLE);
        } else if (index == 2) {
            btnTabTools.setBackgroundResource(R.drawable.tab_selected_bg);
            btnTabTools.setTextColor(getResources().getColor(R.color.white));
            layoutTools.setVisibility(View.VISIBLE);
        }
    }

    private void updateGlobalStatus(String statusText, int textColor, boolean isOnline) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tvGlobalStatus.setText(statusText);
                tvGlobalStatus.setTextColor(textColor);
            }
        });
    }

    // ==========================================
    // SERVER SIDE CONTROLLER (HOST SERVER)
    // ==========================================
    private void setupServerControls() {
        btnStartServer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startServer();
            }
        });

        btnStopServer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopServer();
            }
        });

        btnSendBroadcast.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String msg = etBroadcastMsg.getText().toString().trim();
                if (!msg.isEmpty()) {
                    broadcastMessage("[SERVER ANNOUNCEMENT] " + msg);
                    logToConsole("[BROADCAST] Sent: " + msg);
                    etBroadcastMsg.setText("");
                }
            }
        });

        btnHostTrivia.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleTriviaHosting();
            }
        });
    }

    private void startServer() {
        if (isServerRunning) return;

        String portStr = etServerPort.getText().toString().trim();
        final int port = portStr.isEmpty() ? 8888 : Integer.parseInt(portStr);

        serverLogBuilder.setLength(0);
        logToConsole("Initializing Local TCP Server Socket...");

        isServerRunning = true;
        btnStartServer.setEnabled(false);
        btnStopServer.setEnabled(true);
        etServerPort.setEnabled(false);
        cardServerActions.setVisibility(View.VISIBLE);

        updateGlobalStatus("● SERVER RUNNING", getResources().getColor(R.color.accent_color), true);

        serverThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    serverSocket = new ServerSocket(port);
                    String localIp = getLocalIpAddress();
                    logToConsole("🟢 Server is ONLINE!");
                    logToConsole("Listening on IP: " + localIp + " Port: " + port);
                    logToConsole("Tell other players to connect to this address!");

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tvServerIpInfo.setText("IP: " + localIp + ":" + port);
                        }
                    });

                    while (isServerRunning) {
                        Socket clientSocket = serverSocket.accept();
                        if (!isServerRunning) break;

                        // Create new client handler thread
                        ClientHandler handler = new ClientHandler(clientSocket);
                        connectedClients.add(handler);
                        new Thread(handler).start();
                    }
                } catch (Exception e) {
                    if (isServerRunning) {
                        logToConsole("❌ Server error: " + e.getMessage());
                    }
                } finally {
                    cleanUpServer();
                }
            }
        });
        serverThread.start();
    }

    private void stopServer() {
        logToConsole("Shutting down Server...");
        isServerRunning = false;
        cleanUpServer();
    }

    private void cleanUpServer() {
        isServerRunning = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (Exception ignored) {}

        // Disconnect all clients safely
        synchronized (connectedClients) {
            for (ClientHandler client : connectedClients) {
                client.disconnect();
            }
            connectedClients.clear();
        }

        // Reset game status
        isTriviaRunning = false;
        currentTriviaIndex = -1;
        playerScores.clear();
        currentRoundAnswers.clear();

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                btnStartServer.setEnabled(true);
                btnStopServer.setEnabled(false);
                etServerPort.setEnabled(true);
                cardServerActions.setVisibility(View.GONE);
                btnHostTrivia.setText("LAUNCH MULTIPLAYER TRIVIA");
                btnHostTrivia.setBackgroundColor(getResources().getColor(R.color.accent_color));
                tvServerIpInfo.setText("Local IP: Offline");
            }
        });

        logToConsole("🔴 Server stopped.");
        updateGlobalStatus("● OFFLINE", getResources().getColor(R.color.text_secondary), false);
    }

    private void logToConsole(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                serverLogBuilder.append(text).append("\n");
                tvServerConsole.setText(serverLogBuilder.toString());
                scrollServerConsole.post(new Runnable() {
                    @Override
                    public void run() {
                        scrollServerConsole.fullScroll(View.FOCUS_DOWN);
                    }
                });
            }
        });
    }

    private void broadcastMessage(String msg) {
        synchronized (connectedClients) {
            for (ClientHandler client : connectedClients) {
                client.sendMessage(msg);
            }
        }
    }

    // Server-Side: Manage Multiplayer Trivia Game
    private void handleTriviaHosting() {
        if (!isTriviaRunning) {
            // Start the game!
            isTriviaRunning = true;
            currentTriviaIndex = 0;
            playerScores.clear();
            currentRoundAnswers.clear();

            // Populate all connected clients into scorer list
            synchronized (connectedClients) {
                for (ClientHandler c : connectedClients) {
                    playerScores.put(c.playerName, 0);
                }
            }

            broadcastMessage("/game_start");
            logToConsole("🎮 Trivia Game Started!");
            sendTriviaQuestion();
        } else {
            // Evaluates previous question answers or proceeds to next
            if (currentTriviaIndex >= 0 && currentTriviaIndex < triviaQuestions.length) {
                String correctAnswer = triviaQuestions[currentTriviaIndex][5];
                String qText = triviaQuestions[currentTriviaIndex][0];

                logToConsole("Evaluating answers for Question " + (currentTriviaIndex + 1) + "...");
                
                // Read and award scores
                StringBuilder scoreReport = new StringBuilder("--- SCOREBOARD ---");
                synchronized (connectedClients) {
                    for (ClientHandler c : connectedClients) {
                        String ans = currentRoundAnswers.get(c.playerName);
                        if (correctAnswer.equalsIgnoreCase(ans)) {
                            int currentScore = playerScores.containsKey(c.playerName) ? playerScores.get(c.playerName) : 0;
                            playerScores.put(c.playerName, currentScore + 10);
                            logToConsole("✨ Player " + c.playerName + " answered Correctly (+10 pts)!");
                        } else {
                            logToConsole("❌ Player " + c.playerName + " answered wrongly or skipped (Choice: " + (ans != null ? ans : "None") + ").");
                        }
                    }
                }

                // Prepare scoreboard message
                for (Map.Entry<String, Integer> entry : playerScores.entrySet()) {
                    scoreReport.append("\n").append(entry.getKey()).append(": ").append(entry.getValue()).append(" points");
                }

                broadcastMessage("[SYSTEM] Round Answer: " + correctAnswer + " (" + triviaQuestions[currentTriviaIndex][Integer.parseInt(correctAnswer.equals("A") ? "1" : correctAnswer.equals("B") ? "2" : correctAnswer.equals("C") ? "3" : "4")] + ")");
                broadcastMessage(scoreReport.toString());
                logToConsole(scoreReport.toString());

                // Prepare for next question
                currentTriviaIndex++;
                if (currentTriviaIndex < triviaQuestions.length) {
                    btnHostTrivia.setText("SEND NEXT QUESTION");
                    btnHostTrivia.setBackgroundColor(getResources().getColor(R.color.primary_color));
                } else {
                    btnHostTrivia.setText("FINISH GAME & SHOW CHAMPION");
                    btnHostTrivia.setBackgroundColor(getResources().getColor(R.color.red_alert));
                }
                currentRoundAnswers.clear();
            } else {
                // Game Over! Declare Winner
                String winner = "No Players connected";
                int topScore = -1;
                for (Map.Entry<String, Integer> entry : playerScores.entrySet()) {
                    if (entry.getValue() > topScore) {
                        topScore = entry.getValue();
                        winner = entry.getKey();
                    }
                }

                broadcastMessage("/game_end");
                broadcastMessage("🏆 GAME OVER! The champion is " + winner + " with " + topScore + " points!");
                logToConsole("🏆 Game Finished. Champion: " + winner + " (" + topScore + " pts)");

                // Reset state
                isTriviaRunning = false;
                currentTriviaIndex = -1;
                btnHostTrivia.setText("LAUNCH MULTIPLAYER TRIVIA");
                btnHostTrivia.setBackgroundColor(getResources().getColor(R.color.accent_color));
            }
        }
    }

    private void sendTriviaQuestion() {
        if (currentTriviaIndex < 0 || currentTriviaIndex >= triviaQuestions.length) return;

        String[] currentQ = triviaQuestions[currentTriviaIndex];
        String qMessage = "/quiz_q " + currentQ[0] + " | " + currentQ[1] + " | " + currentQ[2] + " | " + currentQ[3] + " | " + currentQ[4];
        
        broadcastMessage(qMessage);
        logToConsole("📢 Dispatched Question " + (currentTriviaIndex + 1) + ": " + currentQ[0]);
        
        btnHostTrivia.setText("EVALUATE ROUND & SHOW SCORE");
        btnHostTrivia.setBackgroundColor(getResources().getColor(R.color.primary_dark_color));
    }

    // Server-Side Client Handler Runnable
    private class ClientHandler implements Runnable {
        private final Socket socket;
        private BufferedReader reader;
        private PrintWriter writer;
        private String playerName = "Anonymous";
        private boolean isHandlerConnected = true;

        public ClientHandler(Socket s) {
            this.socket = s;
            try {
                this.reader = new BufferedReader(new InputStreamReader(s.getInputStream()));
                this.writer = new PrintWriter(s.getOutputStream(), true);
                this.playerName = s.getRemoteSocketAddress().toString();
            } catch (Exception e) {
                logToConsole("Connection handling error: " + e.getMessage());
            }
        }

        @Override
        public void run() {
            try {
                logToConsole("🔌 Client connecting from " + socket.getRemoteSocketAddress());
                sendMessage("[SERVER] Connected to GamerConnect Hub! Please send /join YourName to authenticate.");

                String line;
                while (isHandlerConnected && (line = reader.readLine()) != null) {
                    if (line.startsWith("/join ")) {
                        String oldName = this.playerName;
                        this.playerName = line.substring(6).trim();
                        logToConsole("👤 Client authenticated as player: " + this.playerName);
                        sendMessage("[SERVER] Welcome " + this.playerName + "!");
                        broadcastMessage("[SYSTEM] Player " + this.playerName + " joined the server lobby.");
                        
                        // Seed points
                        if (isTriviaRunning) {
                            playerScores.put(this.playerName, 0);
                        }
                    } else if (line.startsWith("/chat ")) {
                        String msg = line.substring(6);
                        broadcastMessage("<" + this.playerName + "> " + msg);
                    } else if (line.startsWith("/ans ")) {
                        String ans = line.substring(5).trim().toUpperCase();
                        currentRoundAnswers.put(this.playerName, ans);
                        sendMessage("[SERVER] Answer (" + ans + ") submitted successfully!");
                        logToConsole("📝 Player " + this.playerName + " submitted: " + ans);
                    } else {
                        // General packet message
                        broadcastMessage("<" + this.playerName + "> " + line);
                    }
                }
            } catch (Exception e) {
                // Connection closed
            } finally {
                disconnect();
            }
        }

        public void sendMessage(String msg) {
            try {
                if (writer != null) {
                    writer.println(msg);
                }
            } catch (Exception ignored) {}
        }

        public void disconnect() {
            if (!isHandlerConnected) return;
            isHandlerConnected = false;
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (Exception ignored) {}
            connectedClients.remove(this);
            logToConsole("🔌 Client disconnected: " + playerName);
            broadcastMessage("[SYSTEM] Player " + playerName + " left the server lobby.");
        }
    }


    // ==========================================
    // CLIENT SIDE CONTROLLER (JOIN SERVER)
    // ==========================================
    private void setupClientControls() {
        btnConnectServer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                connectToServer();
            }
        });

        btnDisconnectServer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                disconnectFromServer();
            }
        });

        btnSendChat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendClientChatMessage();
            }
        });

        // Answer selection buttons
        btnOptA.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                submitClientAnswer("A");
            }
        });
        btnOptB.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                submitClientAnswer("B");
            }
        });
        btnOptC.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                submitClientAnswer("C");
            }
        });
        btnOptD.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                submitClientAnswer("D");
            }
        });
    }

    private void connectToServer() {
        if (isClientConnected) return;

        final String ip = etClientIp.getText().toString().trim();
        String portStr = etClientPort.getText().toString().trim();
        final int port = portStr.isEmpty() ? 8888 : Integer.parseInt(portStr);
        final String name = etClientName.getText().toString().trim().isEmpty() ? "Gamer" : etClientName.getText().toString().trim();

        if (ip.isEmpty()) {
            Toast.makeText(this, "Please enter Server IP", Toast.LENGTH_SHORT).show();
            return;
        }

        clientChatBuilder.setLength(0);
        appendClientChat("Connecting to " + ip + ":" + port + "...");

        btnConnectServer.setEnabled(false);
        etClientIp.setEnabled(false);
        etClientPort.setEnabled(false);
        etClientName.setEnabled(false);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    clientSocket = new Socket(ip, port);
                    clientWriter = new PrintWriter(clientSocket.getOutputStream(), true);
                    isClientConnected = true;

                    appendClientChat("🟢 Connection Established!");
                    
                    // Identify player name to server
                    clientWriter.println("/join " + name);

                    updateGlobalStatus("● CLIENT CONNECTED", getResources().getColor(R.color.primary_color), false);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            btnDisconnectServer.setEnabled(true);
                            layoutClientChatInput.setVisibility(View.VISIBLE);
                        }
                    });

                    // Start receiving broadcast packets from server
                    BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                    String line;
                    while (isClientConnected && (line = reader.readLine()) != null) {
                        final String data = line;
                        
                        // Parse special server codes for Trivia Game
                        if (data.startsWith("/quiz_q ")) {
                            parseAndShowQuiz(data.substring(8));
                        } else if (data.equals("/game_start")) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    layoutTriviaPane.setVisibility(View.VISIBLE);
                                    tvTriviaHeader.setText("🎮 LOBBY GAME STARTED!");
                                    tvTriviaQuestion.setText("Waiting for server to release Question 1...");
                                    resetQuizButtons();
                                }
                            });
                        } else if (data.equals("/game_end")) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    layoutTriviaPane.setVisibility(View.GONE);
                                }
                            });
                        } else {
                            // Standard text/chat broadcast
                            appendClientChat(data);
                        }
                    }

                } catch (Exception e) {
                    appendClientChat("❌ Connection error: " + e.getMessage());
                    resetClientUI();
                } finally {
                    disconnectFromServer();
                }
            }
        }).start();
    }

    private void disconnectFromServer() {
        if (!isClientConnected) return;
        isClientConnected = false;
        try {
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
        } catch (Exception ignored) {}

        resetClientUI();
        appendClientChat("🔌 Connection closed.");
        updateGlobalStatus("● OFFLINE", getResources().getColor(R.color.text_secondary), false);
    }

    private void resetClientUI() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                btnConnectServer.setEnabled(true);
                btnDisconnectServer.setEnabled(false);
                etClientIp.setEnabled(true);
                etClientPort.setEnabled(true);
                etClientName.setEnabled(true);
                layoutClientChatInput.setVisibility(View.GONE);
                layoutTriviaPane.setVisibility(View.GONE);
            }
        });
    }

    private void appendClientChat(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                clientChatBuilder.append(text).append("\n");
                tvClientChat.setText(clientChatBuilder.toString());
                scrollClientChat.post(new Runnable() {
                    @Override
                    public void run() {
                        scrollClientChat.fullScroll(View.FOCUS_DOWN);
                    }
                });
            }
        });
    }

    private void sendClientChatMessage() {
        String msg = etClientChatMsg.getText().toString().trim();
        if (!msg.isEmpty() && isClientConnected) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        clientWriter.println("/chat " + msg);
                    } catch (Exception ignored) {}
                }
            }).start();
            etClientChatMsg.setText("");
        }
    }

    private void parseAndShowQuiz(final String quizData) {
        // Format: Question | Opt A | Opt B | Opt C | Opt D
        String[] parts = quizData.split("\\|");
        if (parts.length >= 5) {
            final String q = parts[0].trim();
            final String a = parts[1].trim();
            final String b = parts[2].trim();
            final String c = parts[3].trim();
            final String d = parts[4].trim();

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    layoutTriviaPane.setVisibility(View.VISIBLE);
                    tvTriviaHeader.setText("🎮 CHOOSE YOUR ANSWER:");
                    tvTriviaQuestion.setText(q);
                    btnOptA.setText("A: " + a);
                    btnOptB.setText("B: " + b);
                    btnOptC.setText("C: " + c);
                    btnOptD.setText("D: " + d);
                    resetQuizButtons();
                    enableQuizButtons(true);
                }
            });
        }
    }

    private void submitClientAnswer(final String choice) {
        if (!isClientConnected) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                clientWriter.println("/ans " + choice);
            }
        }).start();

        enableQuizButtons(false);
        // Highlight chosen button
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (choice.equals("A")) btnOptA.setBackgroundColor(getResources().getColor(R.color.accent_color));
                else if (choice.equals("B")) btnOptB.setBackgroundColor(getResources().getColor(R.color.accent_color));
                else if (choice.equals("C")) btnOptC.setBackgroundColor(getResources().getColor(R.color.accent_color));
                else if (choice.equals("D")) btnOptD.setBackgroundColor(getResources().getColor(R.color.accent_color));
            }
        });
    }

    private void enableQuizButtons(final boolean enabled) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                btnOptA.setEnabled(enabled);
                btnOptB.setEnabled(enabled);
                btnOptC.setEnabled(enabled);
                btnOptD.setEnabled(enabled);
            }
        });
    }

    private void resetQuizButtons() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                btnOptA.setBackgroundColor(getResources().getColor(R.color.white));
                btnOptB.setBackgroundColor(getResources().getColor(R.color.white));
                btnOptC.setBackgroundColor(getResources().getColor(R.color.white));
                btnOptD.setBackgroundColor(getResources().getColor(R.color.white));
            }
        });
    }


    // ==========================================
    // DIAGNOSTICS & NETWORK TOOLS CONTROLLER
    // ==========================================
    private void setupToolsControls() {
        btnPingTest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runPingTest();
            }
        });

        btnStartScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runPortScan();
            }
        });

        btnSendWol.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runWakeOnLan();
            }
        });
    }

    private void updateLocalNetworkInfo() {
        ConnectivityManager connManager = (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo mWifi = connManager != null ? connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI) : null;
        
        if (mWifi != null && mWifi.isConnected()) {
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo wifiInfo = wifiManager != null ? wifiManager.getConnectionInfo() : null;
            if (wifiInfo != null) {
                String ssid = wifiInfo.getSSID();
                int ipAddress = wifiInfo.getIpAddress();
                String ipString = Formatter.formatIpAddress(ipAddress);
                
                tvToolsNetworkDetails.setText("✅ CONNECTED TO LOCAL WIFI\n" +
                        "SSID Name: " + ssid + "\n" +
                        "Device IP Address: " + ipString + "\n" +
                        "Network Speed: " + wifiInfo.getLinkSpeed() + " Mbps\n" +
                        "Frequency: " + (wifiInfo.getFrequency() / 1000.0) + " GHz");
                
                etPingIp.setText(ipString.substring(0, ipString.lastIndexOf('.')) + ".1");
                etScanTargetIp.setText(ipString.substring(0, ipString.lastIndexOf('.')) + ".1");
                return;
            }
        }
        
        // Fallback for Hotspot or Mobile Local Network
        String localAddress = getLocalIpAddress();
        tvToolsNetworkDetails.setText("⚠️ WIFI UNCONNECTED OR HOTSPOT ACTIVE\n" +
                "Detected Local IP: " + localAddress + "\n" +
                "Make sure your devices are linked to the SAME hotspot subnet to connect!");
        etPingIp.setText("192.168.43.1");
        etScanTargetIp.setText("192.168.43.1");
    }

    private String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && !inetAddress.isLinkLocalAddress() && inetAddress.isSiteLocalAddress()) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (SocketException ex) {
            Log.e("IP_LOOKUP", ex.toString());
        }
        return "127.0.0.1";
    }

    private void runPingTest() {
        final String ipStr = etPingIp.getText().toString().trim();
        if (ipStr.isEmpty()) {
            Toast.makeText(this, "Enter an IP address first", Toast.LENGTH_SHORT).show();
            return;
        }

        tvPingResult.setText("Pinging " + ipStr + "...");
        btnPingTest.setEnabled(false);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    long startTime = System.currentTimeMillis();
                    InetAddress address = InetAddress.getByName(ipStr);
                    boolean reachable = address.isReachable(2000); // 2 second timeout
                    long endTime = System.currentTimeMillis();

                    final String res;
                    if (reachable) {
                        res = "🟢 REACHABLE! Latency: " + (endTime - startTime) + " ms";
                    } else {
                        res = "🔴 UNREACHABLE! Host is offline or blocking ICMP packets.";
                    }

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tvPingResult.setText(res);
                            btnPingTest.setEnabled(true);
                        }
                    });
                } catch (Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tvPingResult.setText("❌ Ping Error: " + e.getMessage());
                            btnPingTest.setEnabled(true);
                        }
                    });
                }
            }
        }).start();
    }

    private void runPortScan() {
        final String targetIp = etScanTargetIp.getText().toString().trim();
        if (targetIp.isEmpty()) {
            Toast.makeText(this, "Enter Target IP Address", Toast.LENGTH_SHORT).show();
            return;
        }

        tvScanResults.setText("Scanning standard multiplayer ports...\n");
        btnStartScan.setEnabled(false);

        // Standard multiplayer game server ports to check
        final int[] portsToScan = {
            80, 443,      // Web (for HTML5 games)
            25565,        // Minecraft (Java Edition)
            19132,        // Minecraft Bedrock
            7777,         // Terraria / GTA SAMP
            27015,        // Source Engine / CS / GMod
            3000, 8080, 8888 // Built-in node / debug / game servers
        };

        new Thread(new Runnable() {
            @Override
            public void run() {
                final StringBuilder scanLog = new StringBuilder("Scan results for " + targetIp + ":\n");
                
                for (final int port : portsToScan) {
                    try {
                        Socket socket = new Socket();
                        socket.connect(new java.net.InetSocketAddress(targetIp, port), 250); // 250ms short timeout
                        socket.close();
                        scanLog.append("🟢 PORT ").append(port).append(" [OPEN] - Server detected!\n");
                    } catch (Exception e) {
                        scanLog.append("🔴 PORT ").append(port).append(" [CLOSED]\n");
                    }

                    // Update UI as we scan each port
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tvScanResults.setText(scanLog.toString());
                        }
                    });
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        btnStartScan.setEnabled(true);
                    }
                });
            }
        }).start();
    }

    private void runWakeOnLan() {
        final String macStr = etWolMac.getText().toString().trim();
        if (macStr.isEmpty()) {
            Toast.makeText(this, "Enter Network MAC Address", Toast.LENGTH_SHORT).show();
            return;
        }

        tvWolStatus.setText("Assembling Magic Packet...");
        btnSendWol.setEnabled(false);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Parse MAC address bytes
                    String[] hex = macStr.split("(\\:|\\-)");
                    if (hex.length != 6) {
                        throw new IllegalArgumentException("MAC address must be in format XX:XX:XX:XX:XX:XX");
                    }
                    byte[] macBytes = new byte[6];
                    for (int i = 0; i < 6; i++) {
                        macBytes[i] = (byte) Integer.parseInt(hex[i], 16);
                    }

                    // Construct WOL Magic Packet: 6 bytes of 0xFF, followed by 16 repetitions of macBytes
                    byte[] bytes = new byte[6 + 16 * macBytes.length];
                    for (int i = 0; i < 6; i++) {
                        bytes[i] = (byte) 0xff;
                    }
                    for (int i = 6; i < bytes.length; i += macBytes.length) {
                        System.arraycopy(macBytes, 0, bytes, i, macBytes.length);
                    }

                    // Send magic packet to broadcast IP via UDP port 9
                    InetAddress address = InetAddress.getByName("255.255.255.255");
                    DatagramPacket packet = new DatagramPacket(bytes, bytes.length, address, 9);
                    DatagramSocket socket = new DatagramSocket();
                    socket.setBroadcast(true);
                    socket.send(packet);
                    socket.close();

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tvWolStatus.setText("🟢 Magic Packet broadcasted successfully!");
                            btnSendWol.setEnabled(true);
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tvWolStatus.setText("❌ Error: " + e.getMessage());
                            btnSendWol.setEnabled(true);
                        }
                    });
                }
            }
        }).start();
    }
}
