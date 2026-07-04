package br.org.gam.api.member.web;

import br.org.gam.api.member.application.MemberRDTO;
import br.org.gam.api.member.application.useCases.Activation;
import br.org.gam.api.member.application.useCases.DeactivateMemberDTO;
import br.org.gam.api.member.application.useCases.GetMember;
import br.org.gam.api.member.application.useCases.RegisterMember.RegisterMember;
import br.org.gam.api.member.application.useCases.RegisterMember.RegisterMemberDTO;
import br.org.gam.api.member.application.useCases.RegisterMember.RegisterMemberRDTO;
import br.org.gam.api.member.application.useCases.SearchMembers;
import br.org.gam.api.presence.application.PresenceRDTO;
import br.org.gam.api.presence.application.useCases.GetPresence;
import br.org.gam.api.rbac.Permission.domain.PermissionEnum;
import br.org.gam.api.shared.specification.SearchDTO;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/members")
public class MemberController {

    private final RegisterMember registerMember;
    private final GetMember getMember;
    private final SearchMembers searchMembers;
    private final Activation activation;
    private final GetPresence getPresence;

    public MemberController(RegisterMember registerMember, GetMember getMember, SearchMembers searchMembers,
                            Activation activation, GetPresence getPresence
    ) {

        this.registerMember = registerMember;
        this.getMember = getMember;
        this.searchMembers = searchMembers;
        this.activation = activation;
        this.getPresence = getPresence;
    }

    @PostMapping
    public ResponseEntity<RegisterMemberRDTO> registerMember(@RequestBody @Valid RegisterMemberDTO dto) {

        RegisterMemberRDTO responseDTO = registerMember.register(dto);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(responseDTO.id())
                .toUri();

        return ResponseEntity.created(location).body(responseDTO);
    }

    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.MEMBER_GET + "')")
    @GetMapping("/{id}")
    public ResponseEntity<MemberRDTO> getMemberById(@PathVariable UUID id) {
        MemberRDTO dto = getMember.byId(id);
        return ResponseEntity.ok(dto);
    }

    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.MEMBER_SEARCH + "')")
    @PostMapping("/search")
    public ResponseEntity<Page<MemberRDTO>> searchMembers(@RequestBody @Valid SearchDTO searchDTO,
                                                          Pageable pageable) {

        return ResponseEntity.ok(
                searchMembers.search(searchDTO, pageable)
        );
    }

    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.MEMBER_ACTIVATION + "')")
    @PatchMapping("/{id}/activate")
    public ResponseEntity<Void> activate(@PathVariable UUID id) {

        activation.activate(id);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.MEMBER_ACTIVATION + "')")
    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id, @RequestBody @Valid DeactivateMemberDTO dto) {

        activation.deactivate(id, dto.reason());
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("@memberSecurity.canGetMemberPresences(#memberId)")
    @GetMapping("/{memberId}/presences")
    public ResponseEntity<Page<PresenceRDTO>> getMemberPresences(@PathVariable UUID memberId,
                                                                 Pageable pageable) {

        return ResponseEntity.ok(
                getPresence.allByMember(memberId, pageable)
        );
    }
}
