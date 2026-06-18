package com.project.puzzlecoop;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class WebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(WebSocketHandler.class);
    
    private final PuzzleManager puzzleManager;
    private final ObjectMapper mapper = new ObjectMapper();

    private final CopyOnWriteArrayList<WebSocketSession> clients = new CopyOnWriteArrayList<>();
    
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public WebSocketHandler(PuzzleManager puzzleManager) {
        this.puzzleManager = puzzleManager;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        clients.add(session);
        sendStateToSession(session);
        broadcastStateUpdate();
        broadcastSystemMessage("Player " + getShortId(session) + " has joined the game!");
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        clients.remove(session);
        
        
        for (JigsawPiece piece : puzzleManager.getPieces()) { // unlock the pieces the leaving player was holding
            if (session.getId().equals(piece.getLockedBy())) {
                piece.setLockedBy(null);
            }
        }

        puzzleManager.removeVote(session.getId());
        broadcastSystemMessage("Player " + getShortId(session) + " has left the game!");
        broadcastStateUpdate();
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            Map<String, Object> payload = mapper.readValue(message.getPayload(), Map.class);

            String action = (String) payload.getOrDefault("action", "");
            String sessionId = session.getId();

            // System.out.println("packet " + action + " from " + sessionId);

            if ("reset".equals(action)) {
                puzzleManager.resetPuzzle();
                broadcastStateUpdate();
            } else if ("chat".equals(action)) {
                String msg = (String) payload.get("msg");
                broadcastMessage(Map.of("type", "chat", "id", getShortId(session), "msg", msg));
            } else if ("cursor".equals(action)) {

                double cursorX = ((Number) payload.get("x")).doubleValue();
                double cursorY = ((Number) payload.get("y")).doubleValue();
                broadcastMessageExcluding(sessionId, Map.of("type", "cursor", "id", sessionId, "x", cursorX, "y", cursorY));

            } else if ("vote".equals(action)) {
                handleVote(session);
            } else if ("grab".equals(action)) {
                int pieceId = (Integer) payload.get("pieceId");
                // System.out.println("User " + sessionId + " trying to grab item: " + pieceId);
                puzzleManager.grabPiece(pieceId, sessionId);
                broadcastStateUpdate();
            } else if ("move".equals(action) || "drop".equals(action)) {
                handlePieceMovement(action, payload, sessionId);
            } else {
                System.err.println("unknown action: " + action);
            }

        } catch (Exception e) {
            log.error("cannot get message from {}", session.getId(), e);
        }
    }


    private void handleVote(WebSocketSession session) {
        puzzleManager.setTotalUsers(clients.size());
        boolean votesMet = puzzleManager.castVote(session.getId());
        
        broadcastSystemMessage("Player " + getShortId(session) + " has voted to reset!");

        if (votesMet) {
            puzzleManager.resetPuzzle();
            puzzleManager.resetVotes();
            broadcastSystemMessage("All players have voted to reset the board. Fetching new image...");
            broadcastMessage(Map.of("type", "reset_ui"));
        }
        broadcastStateUpdate();
    }

    private void handlePieceMovement(String action, Map<String, Object> payload, String sessionId) {
        int pieceId = (Integer) payload.get("pieceId");
        
        double x = ((Number) payload.get("x")).doubleValue();
        double y = ((Number) payload.get("y")).doubleValue();

        if ("move".equals(action)) {
            puzzleManager.movePiece(pieceId, x, y, sessionId);
        } else if ("drop".equals(action)) {
            puzzleManager.dropPiece(pieceId, x, y, sessionId);
            checkWinCondition();
        }
        broadcastStateUpdate();
    }

    private void checkWinCondition() {
        if (!puzzleManager.isPuzzleComplete()) return;

        broadcastMessage(Map.of("type", "win"));
        broadcastSystemMessage("Congratulations!!! Fetching new image...");

        scheduler.schedule(() -> {
            
            puzzleManager.resetPuzzle();
            broadcastStateUpdate();
            
        }, 5, TimeUnit.SECONDS); 
    }

 

    private void broadcastSystemMessage(String msg) {
        broadcastMessage(Map.of("type", "chat", "id", "SYSTEM", "msg", msg));
    }

    private void broadcastStateUpdate() {
        broadcastMessage(Map.of(
            "type", "sync", 
            "nodes", clients.size(), 
            "imageId", puzzleManager.getCurrentRoundId(),
            "pieces", puzzleManager.getPieces(),
            "votes", puzzleManager.getVotesToReset()
        ));
    }

    private void sendStateToSession(WebSocketSession session) {
        try {
            Map<String, Object> state = Map.of(
                "type", "init", 
                "mySessionId", session.getId(), 
                "imageId", puzzleManager.getCurrentRoundId(),
                "pieces", puzzleManager.getPieces()
            );
            session.sendMessage(new TextMessage(mapper.writeValueAsString(state)));
        } catch (IOException e) {
            log.error("cannot send state :( {}", session.getId(), e);
        }
    }

    private void broadcastMessage(Map<String, Object> payload) {
        broadcastMessageExcluding(null, payload);
    }

    private void broadcastMessageExcluding(String excludedSessionId, Map<String, Object> payload) {
        try {
            TextMessage msg = new TextMessage(mapper.writeValueAsString(payload));
            for (WebSocketSession s : clients) {
                if (s.isOpen() && (excludedSessionId == null || !s.getId().equals(excludedSessionId))) {
                    s.sendMessage(msg);
                }
            }
        } catch (IOException e) {
            log.error("failed to broadcast message", e);
        }
    }



    private String getShortId(WebSocketSession session) {
        return session.getId().substring(0, 4);
    }
}