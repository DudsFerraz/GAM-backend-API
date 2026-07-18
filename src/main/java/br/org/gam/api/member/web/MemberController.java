package br.org.gam.api.member.web;

import br.org.gam.api.member.application.MemberRDTO;
import br.org.gam.api.member.application.useCases.Activation;
import br.org.gam.api.member.application.useCases.DeactivateMemberDTO;
import br.org.gam.api.member.application.useCases.GetMember;
import br.org.gam.api.member.application.useCases.registerMember.RegisterMember;
import br.org.gam.api.member.application.useCases.registerMember.RegisterMemberDTO;
import br.org.gam.api.member.application.useCases.registerMember.RegisterMemberRDTO;
import br.org.gam.api.member.application.useCases.registerMember.RegisterMemberWorkflow;
import br.org.gam.api.member.application.useCases.SearchMembers;
import br.org.gam.api.presence.application.PresenceRDTO;
import br.org.gam.api.presence.application.useCases.GetPresence;
import br.org.gam.api.rbac.permission.domain.PermissionEnum;
import br.org.gam.api.shared.specification.SearchDTO;
import br.org.gam.api.shared.web.PagedResponse;
import br.org.gam.api.shared.web.PublicApiUri;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/members")
public class MemberController {

    private final RegisterMemberWorkflow registerMember;
    private final GetMember getMember;
    private final SearchMembers searchMembers;
    private final Activation activation;
    private final GetPresence getPresence;

    public MemberController(RegisterMemberWorkflow registerMember, GetMember getMember, SearchMembers searchMembers,
                            Activation activation, GetPresence getPresence
    ) {

        this.registerMember = registerMember;
        this.getMember = getMember;
        this.searchMembers = searchMembers;
        this.activation = activation;
        this.getPresence = getPresence;
    }

    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.MEMBER_MANAGE + "')")
    @Operation(operationId = "createMember")
    @PostMapping
    public ResponseEntity<MemberRDTO> registerMember(@RequestBody @Valid RegisterMemberDTO dto) {

        MemberRDTO responseDTO = registerMember.register(dto);

        return ResponseEntity.created(PublicApiUri.forResource("/members/" + responseDTO.id()))
                .body(responseDTO);
    }

    @PreAuthorize("@memberSecurity.canGetMemberById(#id)")
    @Operation(operationId = "getMember")
    @GetMapping("/{id}")
    public ResponseEntity<MemberRDTO> getMemberById(@PathVariable UUID id) {
        MemberRDTO dto = getMember.byId(id);
        return ResponseEntity.ok(dto);
    }

    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.MEMBER_SEARCH + "')")
    @Operation(operationId = "searchMembers")
    @PostMapping("/search")
    public ResponseEntity<PagedResponse<MemberRDTO>> searchMembers(@RequestBody @Valid SearchDTO searchDTO,
                                                                     Pageable pageable) {

        return ResponseEntity.ok(PagedResponse.from(searchMembers.search(searchDTO, pageable)));
    }

    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.MEMBER_ACTIVATION + "')")
    @Operation(operationId = "activateMember")
    @PatchMapping("/{id}/activate")
    public ResponseEntity<Void> activate(@PathVariable UUID id, @RequestBody @Valid DeactivateMemberDTO dto) {

        activation.activate(id, dto.reason());
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.MEMBER_ACTIVATION + "')")
    @Operation(operationId = "deactivateMember")
    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id, @RequestBody @Valid DeactivateMemberDTO dto) {

        activation.deactivate(id, dto.reason());
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("@memberSecurity.canGetMemberPresences(#memberId)")
    @Operation(operationId = "getMemberPresences")
    @GetMapping("/{memberId}/presences")
    public ResponseEntity<PagedResponse<PresenceRDTO>> getMemberPresences(@PathVariable UUID memberId,
                                                                            Pageable pageable) {

        return ResponseEntity.ok(PagedResponse.from(getPresence.allByMember(memberId, pageable)));
    }
}
