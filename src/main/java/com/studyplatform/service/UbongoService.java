package com.studyplatform.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyplatform.dto.request.StudyMoveRequest;
import com.studyplatform.dto.response.StudyStateResponse;
import com.studyplatform.model.Player;
import com.studyplatform.model.Room;
import com.studyplatform.model.StudyStatus;
import com.studyplatform.model.StudyType;
import com.studyplatform.model.ubongo.*;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class UbongoService {

    private final ObjectMapper mapper;

    public UbongoService(ObjectMapper mapper) { this.mapper = mapper; }

    // ── Move processing ───────────────────────────────────────────────────────

    public StudyStateResponse processMove(Room room, Player player, StudyMoveRequest req) {
        UbongoGame game = getOrInit(room);
        int pi = room.getPlayers().indexOf(player);

        switch (req.getMoveType() == null ? "" : req.getMoveType()) {

            case "UBONGO_PLACE": {
                if (req.getPayload() == null) return buildState(room, game, "ERROR: Missing payload");
                Map<String, Object> p;
                try { p = mapper.convertValue(req.getPayload(), new TypeReference<>() {}); }
                catch (Exception e) { return buildState(room, game, "ERROR: Invalid payload"); }

                String pieceId   = (String) p.get("pieceId");
                int row          = toInt(p.get("row"));
                int col          = toInt(p.get("col"));
                int orientIdx    = toInt(p.get("orientationIndex"));

                String err = game.placePiece(pi, pieceId, row, col, orientIdx);
                if (err != null) return buildState(room, game, "ERROR: " + err);

                if (game.getWinner() >= 0) {
                    room.setStatus(StudyStatus.FINISHED);
                    String winner = room.getPlayers().get(game.getWinner()).getNickname();
                    return buildState(room, game, winner + " 클리어!");
                }
                return buildState(room, game, "");
            }

            case "UBONGO_REMOVE": {
                if (req.getPayload() == null) return buildState(room, game, "ERROR: Missing payload");
                Map<String, Object> p;
                try { p = mapper.convertValue(req.getPayload(), new TypeReference<>() {}); }
                catch (Exception e) { return buildState(room, game, "ERROR: Invalid payload"); }

                String pieceId = (String) p.get("pieceId");
                game.removePiece(pi, pieceId);
                return buildState(room, game, "");
            }

            default:
                return buildState(room, game, "ERROR: Unknown moveType " + req.getMoveType());
        }
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    public StudyStateResponse buildInitialState(Room room) {
        return buildState(room, getOrInit(room), "");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UbongoGame getOrInit(Room room) {
        if (!(room.getGameData() instanceof UbongoGame))
            room.setGameData(new UbongoGame(room.getPlayers().size()));
        return (UbongoGame) room.getGameData();
    }

    private StudyStateResponse buildState(Room room, UbongoGame game, String message) {
        String[] names = room.getPlayers().stream().map(Player::getNickname).toArray(String[]::new);
        return StudyStateResponse.builder()
                .roomId(room.getRoomId())
                .studyType(StudyType.UBONGO)
                .status(room.getStatus())
                .message(message)
                .currentTurn(0)
                .winner(game.getWinner())
                .gameData(buildGameData(game))
                .playerNames(names)
                .build();
    }

    private Map<String, Object> buildGameData(UbongoGame game) {
        Map<String, Object> data = new LinkedHashMap<>();

        // ── Puzzle ────────────────────────────────────────────────────────────
        PuzzleCard puzzle = game.getPuzzle();
        Map<String, Object> puzzleMap = new LinkedHashMap<>();

        // blocked: 5x5 boolean array
        puzzleMap.put("blocked", puzzle.getBlocked());

        // pieces: id, color, size, orientations
        List<Map<String, Object>> piecesList = new ArrayList<>();
        for (String pid : puzzle.getPieceIds()) {
            UbongoPiece piece = UbongoPiece.get(pid);
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("id", piece.id);
            pm.put("color", piece.color);
            pm.put("size", piece.size);
            // orientations: List<int[][]> → Jackson serializes as [[[r,c],...],...]
            pm.put("orientations", piece.orientations);
            piecesList.add(pm);
        }
        puzzleMap.put("pieces", piecesList);
        data.put("puzzle", puzzleMap);

        // ── Player states ────────────────────────────────────────────────────
        List<Map<String, Object>> states = new ArrayList<>();
        for (UbongoPlayerState ps : game.getPlayerStates()) {
            Map<String, Object> s = new LinkedHashMap<>();
            // placements: Map<pieceId, {row,col,orientationIndex}>
            Map<String, Object> placementsMap = new LinkedHashMap<>();
            for (Map.Entry<String, UbongoPlayerState.PlacedPiece> e : ps.getPlacements().entrySet()) {
                UbongoPlayerState.PlacedPiece pp = e.getValue();
                Map<String, Integer> ppMap = new LinkedHashMap<>();
                ppMap.put("row", pp.getRow());
                ppMap.put("col", pp.getCol());
                ppMap.put("orientationIndex", pp.getOrientationIndex());
                placementsMap.put(e.getKey(), ppMap);
            }
            s.put("placements", placementsMap);
            s.put("solved", ps.isSolved());
            s.put("solveTimeMs", ps.getSolveTimeMs());
            states.add(s);
        }
        data.put("playerStates", states);
        data.put("winner", game.getWinner());

        return data;
    }

    private static int toInt(Object v) {
        if (v instanceof Number) return ((Number) v).intValue();
        return -1;
    }
}
