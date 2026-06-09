package com.studyplatform.service;

import com.studyplatform.dto.request.StudyMoveRequest;
import com.studyplatform.dto.response.StudyStateResponse;
import com.studyplatform.model.Player;
import com.studyplatform.model.Room;
import com.studyplatform.model.StudyStatus;
import com.studyplatform.model.StudyType;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class TetrisService {

    public StudyStateResponse processMove(Room room, Player player, StudyMoveRequest request) {
        if (!"TETRIS_SYNC".equals(request.getMoveType())) {
            throw new IllegalArgumentException("Unknown TETRIS move.");
        }
        return buildInitialState(room);
    }

    public StudyStateResponse buildInitialState(Room room) {
        Map<String, Object> gameData = new HashMap<>();
        gameData.put("mode", "local");
        gameData.put("rows", 20);
        gameData.put("cols", 10);

        String[] names = room.getPlayers().stream().map(Player::getNickname).toArray(String[]::new);
        return StudyStateResponse.builder()
                .roomId(room.getRoomId())
                .studyType(StudyType.TETRIS)
                .status(room.getStatus())
                .message("TETRIS queue monitor ready.")
                .currentTurn(0)
                .winner(room.getStatus() == StudyStatus.FINISHED ? 0 : -1)
                .gameData(gameData)
                .playerNames(names)
                .build();
    }
}
