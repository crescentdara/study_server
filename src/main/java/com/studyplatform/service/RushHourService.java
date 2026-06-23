package com.studyplatform.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyplatform.dto.request.StudyMoveRequest;
import com.studyplatform.dto.response.StudyStateResponse;
import com.studyplatform.model.Player;
import com.studyplatform.model.Room;
import com.studyplatform.model.StudyStatus;
import com.studyplatform.model.StudyType;
import com.studyplatform.model.rushhour.RushHourGame;
import com.studyplatform.model.rushhour.RushHourPlayerState;
import com.studyplatform.model.rushhour.RushHourVehicle;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class RushHourService {

    private final ObjectMapper objectMapper;

    public RushHourService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public StudyStateResponse processMove(Room room, Player player, StudyMoveRequest request) {
        if (!(room.getGameData() instanceof RushHourGame)) {
            room.setGameData(new RushHourGame(room.getPlayers().size()));
        }
        RushHourGame game = (RushHourGame) room.getGameData();
        int playerIndex = room.getPlayers().indexOf(player);

        if ("RUSH_MOVE".equals(request.getMoveType())) {
            if (request.getPayload() == null) return buildState(room, game, "ERROR: Missing payload");
            Map<String, Integer> payload;
            try {
                payload = objectMapper.convertValue(request.getPayload(), new TypeReference<>() {});
            } catch (Exception e) {
                return buildState(room, game, "ERROR: Invalid payload");
            }
            int vehicleId  = payload.getOrDefault("vehicleId", -1);
            int targetRow  = payload.getOrDefault("targetRow", -1);
            int targetCol  = payload.getOrDefault("targetCol", -1);

            String err = game.moveVehicle(playerIndex, vehicleId, targetRow, targetCol);
            if (err != null) return buildState(room, game, "ERROR: " + err);

            if (game.getWinner() >= 0) {
                String[] names = room.getPlayers().stream().map(Player::getNickname).toArray(String[]::new);
                room.setStatus(StudyStatus.FINISHED);
                return StudyStateResponse.builder()
                        .roomId(room.getRoomId()).studyType(StudyType.RUSH_HOUR)
                        .status(StudyStatus.FINISHED)
                        .message(names[game.getWinner()] + " 승리!")
                        .currentTurn(game.getWinner()).winner(game.getWinner())
                        .gameData(buildGameData(game)).playerNames(names).build();
            }
            return buildState(room, game, "");
        }

        return buildState(room, game, "ERROR: Unknown moveType");
    }

    public StudyStateResponse buildInitialState(Room room) {
        if (!(room.getGameData() instanceof RushHourGame)) {
            room.setGameData(new RushHourGame(room.getPlayers().size()));
        }
        return buildState(room, (RushHourGame) room.getGameData(), "");
    }

    private StudyStateResponse buildState(Room room, RushHourGame game, String message) {
        String[] names = room.getPlayers().stream().map(Player::getNickname).toArray(String[]::new);
        return StudyStateResponse.builder()
                .roomId(room.getRoomId()).studyType(StudyType.RUSH_HOUR)
                .status(room.getStatus()).message(message)
                .currentTurn(0).winner(game.getWinner())
                .gameData(buildGameData(game)).playerNames(names).build();
    }

    private Map<String, Object> buildGameData(RushHourGame game) {
        Map<String, Object> data = new HashMap<>();
        data.put("numPlayers", game.getNumPlayers());
        data.put("puzzleIndex", game.getPuzzleIndex());
        data.put("winner", game.getWinner());
        data.put("startTime", game.getStartTime());

        List<Map<String, Object>> states = new ArrayList<>();
        for (RushHourPlayerState ps : game.getPlayerStates()) {
            Map<String, Object> s = new HashMap<>();
            s.put("moves", ps.getMoves());
            s.put("solved", ps.isSolved());
            s.put("solveTimeMs", ps.getSolveTimeMs());
            List<Map<String, Object>> vList = new ArrayList<>();
            for (RushHourVehicle v : ps.getVehicles()) {
                Map<String, Object> vm = new HashMap<>();
                vm.put("id", v.getId());
                vm.put("row", v.getRow());
                vm.put("col", v.getCol());
                vm.put("length", v.getLength());
                vm.put("horizontal", v.isHorizontal());
                vm.put("color", v.getColor());
                vList.add(vm);
            }
            s.put("vehicles", vList);
            states.add(s);
        }
        data.put("playerStates", states);
        return data;
    }
}
