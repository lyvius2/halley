package banghak.home.halley.adapter.inbound.web;

import banghak.home.halley.adapter.inbound.web.dto.GroupInviteResponse;
import banghak.home.halley.adapter.inbound.web.dto.GroupRenameRequest;
import banghak.home.halley.adapter.inbound.web.dto.GroupDetailResponse;
import banghak.home.halley.adapter.inbound.web.dto.GroupResponse;
import banghak.home.halley.adapter.inbound.web.dto.GroupWebhookRequest;
import banghak.home.halley.adapter.inbound.web.dto.JoinGroupRequest;
import banghak.home.halley.application.service.GroupService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 그룹 (설계 I89).
 *
 * <p><b>다른 그룹 목록을 주는 API가 없습니다</b>(규칙 7) — 회원은 어떤 그룹이 있는지도 알 수
 * 없어야 합니다. admin의 그룹 목록은 관리자 API에 따로 둡니다.
 */
@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @GetMapping("/me")
    public GroupResponse myGroup() {
        return groupService.myGroup();
    }

    /** 그룹 이름은 그 그룹의 누구나 바꾼다 (설계 I87). */
    /** 그룹 정보 화면 (설계 I123). 구성원·현금 합계·매물 수를 한 번에 준다. */
    @GetMapping("/me/detail")
    public GroupDetailResponse myGroupDetail() {
        return groupService.myGroupDetail();
    }

    @PutMapping("/me")
    public GroupResponse rename(@RequestBody GroupRenameRequest request) {
        return groupService.rename(request.name());
    }

    /** 알림이 나갈 Slack 웹훅. 그룹의 누구나 바꾼다 (설계 I96). */
    @PutMapping("/me/webhook")
    public GroupResponse updateWebhook(@RequestBody GroupWebhookRequest request) {
        return groupService.updateWebhook(request.slackWebhookUrl());
    }

    /** 저장된 웹훅으로 테스트 메시지를 보낸다 (설계 I96). */
    @PostMapping("/me/webhook/test")
    public java.util.Map<String, Object> testWebhook() {
        return java.util.Map.of("sent", groupService.testWebhook());
    }

    /** 내 그룹으로 부를 코드를 만든다. 전달은 앱이 하지 않는다 (규칙 10). */
    @PostMapping("/me/invites")
    public GroupInviteResponse createInvite() {
        return groupService.createInvite();
    }

    /** 초대 코드를 받아 그룹을 옮긴다. 원래 그룹이 비면 그 매물까지 사라진다 (규칙 4). */
    @PostMapping("/join")
    public GroupResponse join(@RequestBody JoinGroupRequest request) {
        return groupService.joinByInvite(request.code());
    }
}
