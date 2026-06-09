package com.studyplatform.service;

import com.studyplatform.dto.request.StudyMoveRequest;
import com.studyplatform.dto.response.StudyStateResponse;
import com.studyplatform.model.Player;
import com.studyplatform.model.Room;
import com.studyplatform.model.StudyStatus;
import com.studyplatform.model.StudyType;
import com.studyplatform.model.oldmaid.OldMaidGame;
import org.springframework.stereotype.Service;

/**
 * 도둑잡기(Old Maid) 게임 서비스
 *
 * ─── 담당 moveType ────────────────────────────────────────────────────────
 * DRAW_CARD - 현재 턴 플레이어가 다음 플레이어의 카드를 1장 뽑음
 *             request.getData() = 뽑을 카드의 인덱스(숫자 문자열)
 *
 * ─── 게임 흐름 ────────────────────────────────────────────────────────────
 * START_GAME → PLAYING (SETUP 단계 없음: 카드는 서버가 자동 배분)
 *   ↓ (매 턴 DRAW_CARD)
 * PLAYING (active 플레이어가 1명만 남을 때까지)
 *   ↓
 * FINISHED (loser = 조커 보유자)
 */
@Service
public class OldMaidService {

    /**
     * 게임 액션 처리
     *
     * @param room    현재 방 (gameData = OldMaidGame)
     * @param player  액션을 수행하는 플레이어
     * @param request WebSocket 요청 (moveType + data)
     * @return 브로드캐스트할 게임 상태 응답
     */
    public StudyStateResponse processMove(Room room, Player player, StudyMoveRequest request) {
        OldMaidGame game = (OldMaidGame) room.getGameData();

        String moveType = request.getMoveType();

        // ── 배분 단계: 덱에서 카드 1장 뽑기
        if ("DEAL_CARD".equals(moveType)) {
            String err = game.dealCard(player.getPlayerIndex());
            if (err != null) throw new IllegalStateException(err);
            // 배분이 끝나면 메시지에 안내 추가
            String msg = game.isDealing()
                    ? names(room)[game.getCurrentTurn()] + "'s turn to draw from deck ("
                      + game.getDeckSize() + " cards left)"
                    : "All cards dealt! Now discard your pairs.";
            return buildState(room, game, msg);
        }

        // ── 셔플: 자신의 손패 순서를 섞어 상대의 Joker 추적을 방해
        if ("SHUFFLE_HAND".equals(moveType)) {
            String err = game.shuffleHand(player.getPlayerIndex());
            if (err != null) throw new IllegalStateException(err);
            return buildState(room, game, player.getNickname() + " shuffled their hand! 🔀");
        }

        // ── 쌍 버리기: 손패에서 같은 숫자 카드 2장 선택해서 직접 제거
        //    data 형식: "idx1,idx2"  예) "0,3"
        if ("DISCARD_PAIR".equals(moveType)) {
            String[] parts = request.getData().split(",");
            if (parts.length != 2) throw new IllegalArgumentException("data 형식이 잘못됐습니다. 예: \"0,3\"");
            int idx1, idx2;
            try {
                idx1 = Integer.parseInt(parts[0].trim());
                idx2 = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("카드 인덱스가 숫자가 아닙니다.");
            }
            String err = game.discardPair(player.getPlayerIndex(), idx1, idx2);
            if (err != null) throw new IllegalStateException(err);
            if (game.getLoser() != -1) room.setStatus(StudyStatus.FINISHED);
            return buildState(room, game, null);
        }

        if (!"DRAW_CARD".equals(moveType)) {
            throw new IllegalArgumentException("Unknown moveType: " + moveType);
        }

        // ─ 카드 인덱스 파싱
        int cardIdx;
        try {
            cardIdx = Integer.parseInt(request.getData());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("카드 인덱스가 숫자가 아닙니다: " + request.getData());
        }

        // ─ 카드 뽑기 실행
        String error = game.drawCard(player.getPlayerIndex(), cardIdx);
        if (error != null) throw new IllegalStateException(error);

        // ─ 게임 종료 처리
        if (game.getLoser() != -1) {
            room.setStatus(StudyStatus.FINISHED);
        }

        return buildState(room, game, null);
    }

    /**
     * 방 입장(enterRoom) 시 초기 상태 응답
     * 게임이 이미 시작됐을 때 늦게 입장한 플레이어 화면 동기화에 사용됩니다.
     */
    public StudyStateResponse buildInitialState(Room room) {
        return buildState(room, (OldMaidGame) room.getGameData(), null);
    }

    // ──────────────────────────────────────────────────────────────
    // 내부 헬퍼
    // ──────────────────────────────────────────────────────────────

    /**
     * OldMaidGame 상태 → StudyStateResponse DTO 변환
     *
     * @param overrideMsg null이면 자동 생성, 셔플 등 특별 이벤트는 직접 전달
     */
    private String[] names(Room room) {
        return room.getPlayers().stream().map(Player::getNickname).toArray(String[]::new);
    }

    private StudyStateResponse buildState(Room room, OldMaidGame game, String overrideMsg) {
        String[] names = names(room);

        String message;
        if (overrideMsg != null) {
            message = overrideMsg;
        } else if (game.isDealing()) {
            message = names[game.getCurrentTurn()] + "'s turn to draw from deck ("
                    + game.getDeckSize() + " cards left)";
        } else if (game.getLoser() >= 0) {
            message = "🃏 " + names[game.getLoser()] + " is the THIEF! Game over.";
        } else {
            int next = game.getNextActivePlayer();
            message  = names[game.getCurrentTurn()] + "'s turn — draw from "
                     + names[next] + " (" + game.getHandSizes()[next] + " cards)";
        }

        return StudyStateResponse.builder()
                .roomId(room.getRoomId())
                .studyType(StudyType.OLDMAID)
                .status(room.getStatus())
                .message(message)
                .currentTurn(game.getCurrentTurn())
                .winner(game.getLoser())   // 도둑잡기: winner 필드 = 패자 인덱스
                .gameData(game)            // OldMaidGame 전체 직렬화 (lastShuffle 포함)
                .playerNames(names)
                .build();
    }
}
