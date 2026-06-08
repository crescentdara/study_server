package com.studyplatform.service;

import com.studyplatform.dto.request.StudyMoveRequest;
import com.studyplatform.dto.response.StudyStateResponse;
import com.studyplatform.model.StudyStatus;
import com.studyplatform.model.StudyType;
import com.studyplatform.model.Player;
import com.studyplatform.model.Room;
import com.studyplatform.model.baseball.BaseballGame;
import com.studyplatform.model.baseball.GuessResult;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 숫자야구 게임 로직 서비스 (N명 지원)
 *
 * ─── 서비스 계층의 역할 ────────────────────────────────────────────────────
 * Controller(StudyController) → Service(BaseballService) → Model(BaseballGame)
 *
 * Controller: 요청 수신/검증, 적절한 Service 호출, 응답 브로드캐스트
 * Service   : 실제 비즈니스 로직 (규칙 적용, 상태 전환)
 * Model     : 데이터 구조와 핵심 알고리즘 (Strike/Ball 계산)
 *
 * 이렇게 계층을 나누는 이유:
 *   - 관심사 분리(Separation of Concerns): 각 클래스가 하나의 책임만 가짐
 *   - 테스트 용이: Service만 따로 테스트 가능
 *   - 재사용성: 다른 Controller에서도 이 Service를 사용 가능
 *
 * ─── N명 멀티플레이어 턴 구조 ────────────────────────────────────────────
 * 각 플레이어는 "오른쪽(다음 순서)" 플레이어의 비밀 숫자를 맞춥니다.
 *   3명 예시: 플레이어0 → 1의 숫자, 플레이어1 → 2의 숫자, 플레이어2 → 0의 숫자
 *   targetIndex = (playerIndex + 1) % numPlayers
 */
@Service
public class BaseballService {

    /**
     * 게임 액션 처리 진입점 (라우팅 메서드)
     *
     * switch 표현식(Java 14+):
     *   전통적인 switch-case 대신 화살표(->)로 간결하게 표현
     *   각 case가 값을 반환하므로 String message에 바로 대입 가능
     *   default가 없으면 컴파일 에러 (모든 경우를 처리해야 함)
     *
     * @param room    현재 방 (상태 변경 시 직접 수정됨)
     * @param player  액션을 수행하는 플레이어
     * @param request WebSocket으로 받은 액션 요청
     * @return 클라이언트에 브로드캐스트할 게임 상태 응답
     */
    public StudyStateResponse processMove(Room room, Player player, StudyMoveRequest request) {
        // Room.gameData를 BaseballGame으로 캐스팅
        // (RoomService.initGameData에서 BASEBALL이면 반드시 BaseballGame을 넣었으므로 안전)
        BaseballGame game = (BaseballGame) room.getGameData();
        int digits = game.getDigits(); // 이 방의 자릿수 설정 (3·4·5)

        // moveType에 따라 적절한 핸들러 메서드 호출
        String message = switch (request.getMoveType()) {
            case "SET_SECRET" -> handleSetSecret(room, game, player, request.getData(), digits);
            case "GUESS"      -> handleGuess(room, game, player, request.getData(), digits);
            default -> throw new IllegalArgumentException("알 수 없는 액션: " + request.getMoveType());
        };

        return buildResponse(room, game, message);
    }

    /**
     * 비밀 숫자 설정 처리 (SETUP 단계)
     *
     * 모든 플레이어가 설정하면 PLAYING으로 전환합니다.
     * 남은 인원 수를 계산해서 안내 메시지를 만듭니다.
     */
    private String handleSetSecret(Room room, BaseballGame game, Player player,
                                   String secret, int digits) {
        validateSecret(secret, digits); // 입력값 유효성 검사 (먼저 실행해서 잘못된 입력 차단)

        int idx = player.getPlayerIndex();

        // 이미 설정한 플레이어가 다시 설정하려는 경우 차단
        if (game.getSecretSet()[idx]) {
            throw new IllegalStateException("이미 비밀 숫자를 설정했습니다.");
        }

        // 비밀 숫자 등록
        game.getSecrets()[idx] = secret;
        game.getSecretSet()[idx] = true;

        // 모든 플레이어가 설정 완료했는지 확인
        if (game.allSecretsSet()) {
            room.setStatus(StudyStatus.PLAYING); // SETUP → PLAYING 상태 전환
            return "모두 준비 완료! " + room.getPlayers().get(0).getNickname() + "님이 먼저 추측하세요.";
        }

        // 아직 설정 안 한 인원 수 계산
        long remaining = 0;
        for (boolean b : game.getSecretSet()) if (!b) remaining++;
        return player.getNickname() + "님이 비밀 숫자를 설정했습니다. (" + remaining + "명 대기 중)";
    }

    /**
     * 숫자 추측 처리 (PLAYING 단계)
     *
     * 현재 턴인 플레이어만 추측할 수 있습니다.
     * 추측 결과(Strike/Ball)를 계산하고 히스토리에 기록합니다.
     * 승자가 결정되면 FINISHED 상태로 전환합니다.
     */
    private String handleGuess(Room room, BaseballGame game, Player player,
                                String guess, int digits) {
        // 현재 턴이 아닌 플레이어가 액션하려는 경우 차단
        if (game.getCurrentTurn() != player.getPlayerIndex()) {
            throw new IllegalStateException("지금은 당신의 턴이 아닙니다.");
        }

        validateSecret(guess, digits);

        // 내가 추측해야 할 대상: (내 인덱스 + 1) % 전체 인원 = 오른쪽 플레이어
        int targetIndex = (player.getPlayerIndex() + 1) % game.getNumPlayers();
        String targetName = room.getPlayers().get(targetIndex).getNickname();

        // BaseballGame.addGuess: Strike/Ball 계산 + 히스토리 기록 + 턴 전환
        GuessResult result = game.addGuess(player.getPlayerIndex(), guess);

        // 승자 결정 여부 확인 (digits Strike = 정답)
        if (game.getWinner() >= 0) {
            room.setStatus(StudyStatus.FINISHED);
            String winnerName = room.getPlayers().get(game.getWinner()).getNickname();
            // 게임 종료 시 정답 공개
            return "🎉 " + winnerName + " 승리! " + targetName + "의 정답: " + game.getSecrets()[targetIndex];
        }

        // 다음 턴 플레이어 안내
        String nextName = room.getPlayers().get(game.getCurrentTurn()).getNickname();
        return player.getNickname() + " → " + targetName + ": " + guess
                + " → " + result.getSummary() + " | 다음: " + nextName;
    }

    /**
     * 비밀 숫자 유효성 검사 (추측에도 동일하게 적용)
     *
     * 조건:
     *   1. digits 자리 정확히 일치
     *   2. 각 자리 1~9 (0 불가 — 앞자리 0인 숫자는 암묵적으로 자릿수가 줄어들 수 있어 제외)
     *   3. 모든 자리 숫자가 서로 다름
     *
     * String.chars(): 문자열을 IntStream(문자 코드값 스트림)으로 변환
     * .distinct(): 중복 제거
     * .count(): 고유 문자 수 반환
     */
    private void validateSecret(String secret, int digits) {
        if (secret == null || secret.length() != digits)
            throw new IllegalArgumentException(digits + "자리 숫자를 입력해주세요.");

        // 정규표현식 [1-9]{digits}: digits개의 1~9 숫자로만 구성 확인
        if (!secret.matches("[1-9]{" + digits + "}"))
            throw new IllegalArgumentException("각 자리는 1~9 사이의 숫자만 가능합니다 (0 불가).");

        // distinct().count() == digits: 모든 자리 숫자가 서로 다른지 확인
        if (secret.chars().distinct().count() != digits)
            throw new IllegalArgumentException("각 자리 숫자는 서로 달라야 합니다.");
    }

    /**
     * 현재 게임 상태를 클라이언트 전송용 DTO로 변환
     *
     * Map<String, Object>를 사용하는 이유:
     *   야구와 빙고가 서로 다른 데이터 구조를 가지므로
     *   별도의 응답 DTO를 만들지 않고 Map으로 유연하게 구성합니다.
     *   Jackson이 Map을 자동으로 JSON 객체로 직렬화합니다.
     *
     * 보안: secrets(비밀 숫자)는 게임 종료 전까지 클라이언트에 전송하지 않습니다.
     *   (개발자 도구로 확인 가능하기 때문)
     */
    public StudyStateResponse buildResponse(Room room, BaseballGame game, String message) {
        // 플레이어 닉네임 배열 (Stream의 메서드 레퍼런스 활용)
        String[] playerNames = room.getPlayers().stream()
                .map(Player::getNickname)       // Player::getNickname = p -> p.getNickname()
                .toArray(String[]::new);         // String[]::new = n -> new String[n]

        // 클라이언트에 전달할 게임 데이터 조립
        Map<String, Object> gameData = new HashMap<>();
        gameData.put("digits",         game.getDigits());        // 자릿수
        gameData.put("numPlayers",     game.getNumPlayers());    // 총 인원
        gameData.put("currentTurn",    game.getCurrentTurn());   // 현재 턴
        gameData.put("secretSet",      game.getSecretSet());     // 비밀 설정 여부 배열
        gameData.put("guessHistories", game.getGuessHistories()); // 모든 플레이어 추측 기록
        gameData.put("winner",         game.getWinner());        // 승자 인덱스

        // 게임 종료 시에만 비밀 숫자 공개 (클라이언트에서 정답 표시용)
        if (room.getStatus() == StudyStatus.FINISHED) {
            gameData.put("secrets", game.getSecrets());
        }

        // @Builder 패턴으로 DTO 생성
        return StudyStateResponse.builder()
                .roomId(room.getRoomId())
                .studyType(StudyType.BASEBALL)
                .status(room.getStatus())
                .message(message)
                .currentTurn(game.getCurrentTurn())
                .winner(game.getWinner())
                .gameData(gameData)
                .playerNames(playerNames)
                .build();
    }

    /**
     * 방 입장(enterRoom) 시 초기 상태 응답 생성
     *
     * 새로 입장한 플레이어의 화면을 동기화하기 위해 호출됩니다.
     * SETUP 단계라면 비밀 숫자 설정 안내, PLAYING이라면 진행 중 안내를 보냅니다.
     */
    public StudyStateResponse buildInitialState(Room room) {
        BaseballGame game = (BaseballGame) room.getGameData();
        String msg = room.getStatus() == StudyStatus.SETUP
                ? "각자 " + game.getDigits() + "자리 비밀 숫자를 설정해주세요."
                : "게임 진행 중입니다.";
        return buildResponse(room, game, msg);
    }
}
