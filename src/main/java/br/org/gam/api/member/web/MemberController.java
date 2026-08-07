package br.org.gam.api.member.web;

import br.org.gam.api.member.application.MemberRDTO;
import br.org.gam.api.member.application.useCases.Activation;
import br.org.gam.api.member.application.useCases.DeactivateMemberDTO;
import br.org.gam.api.member.application.useCases.GetMember;
import br.org.gam.api.member.application.useCases.LinkMemberAccountDTO;
import br.org.gam.api.member.application.useCases.MemberInformation;
import br.org.gam.api.member.application.useCases.MemberInformationDTO;
import br.org.gam.api.member.application.MemberInformationRDTO;
import br.org.gam.api.member.application.AnnualMemberInformationRDTO;
import br.org.gam.api.member.application.useCases.GetAnnualMemberInformation;
import br.org.gam.api.member.application.useCases.registerMember.RegisterMemberDTO;
import br.org.gam.api.member.application.useCases.registerMember.RegisterMemberWorkflow;
import br.org.gam.api.member.application.useCases.SearchMembers;
import br.org.gam.api.member.application.useCases.CoordinatorTransitionDTO;
import br.org.gam.api.presence.application.PresenceRDTO;
import br.org.gam.api.presence.application.useCases.GetPresence;
import br.org.gam.api.rbac.permission.domain.PermissionEnum;
import br.org.gam.api.shared.specification.SearchDTO;
import br.org.gam.api.shared.web.PagedResponse;
import br.org.gam.api.shared.web.PublicApiUri;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
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
    private MemberInformation memberInformation;
    private GetAnnualMemberInformation annualMemberInformation;

    public MemberController(RegisterMemberWorkflow registerMember, GetMember getMember, SearchMembers searchMembers,
                            Activation activation, GetPresence getPresence
    ) {

        this.registerMember = registerMember;
        this.getMember = getMember;
        this.searchMembers = searchMembers;
        this.activation = activation;
        this.getPresence = getPresence;
    }

    @Autowired(required = false)
    void configureMemberInformation(MemberInformation memberInformation,
                                    GetAnnualMemberInformation annualMemberInformation) {
        this.memberInformation = memberInformation;
        this.annualMemberInformation = annualMemberInformation;
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
        GetMember.VersionedMember result = getMember.byIdVersioned(id);
        String etag;
        MemberRDTO dto;
        if (result != null) {
            dto = result.body();
            etag = memberInformation == null ? null : memberInformation.etag(result.version());
        } else {
            // Compatibility boundary for isolated controller doubles that predate the versioned read.
            etag = memberInformation == null ? null : memberInformation.etag(id);
            dto = getMember.byId(id);
        }
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (etag != null) response.eTag(etag);
        return response.body(dto);
    }

    @PreAuthorize("@memberSecurity.canGetMemberById(#memberId)")
    @Operation(operationId = "getMemberExperiencesAndSacraments", summary = "Get current Member experiences and sacraments",
            responses = @ApiResponse(responseCode = "200", content = @Content(
                    schema = @Schema(implementation = MemberInformationRDTO.ExperiencesAndSacraments.class),
                    examples = @ExampleObject(value = "{\"experiences\":{\"JORNADA_MISSIONARIA\":\"NOT_INFORMED\","
                            + "\"CURSO_DE_LIDERANCA\":\"NOT_INFORMED\",\"PASCOA_JUVENIL\":\"NOT_INFORMED\","
                            + "\"ACAMPABOSCO\":\"NOT_INFORMED\"},\"sacraments\":{\"BATISMO\":\"NOT_INFORMED\","
                            + "\"PRIMEIRA_COMUNHAO\":\"NOT_INFORMED\",\"CRISMA\":\"NOT_INFORMED\"}}"))))
    @GetMapping("/{memberId}/experiences-and-sacraments")
    public ResponseEntity<MemberInformationRDTO.ExperiencesAndSacraments> experiencesAndSacraments(
            @PathVariable UUID memberId) {
        MemberInformation.Versioned<MemberInformationRDTO.ExperiencesAndSacraments> result =
                memberInformation.versionedExperiencesAndSacraments(memberId);
        return ResponseEntity.ok().eTag(memberInformation.etag(result.version())).body(result.body());
    }

    @PreAuthorize("@memberSecurity.canGetMemberById(#memberId)")
    @Operation(operationId = "getMemberContributionProfile", summary = "Get current Member contribution profile",
            responses = @ApiResponse(responseCode = "200", content = @Content(
                    schema = @Schema(implementation = MemberInformationRDTO.ContributionProfileResponse.class),
                    examples = @ExampleObject(value = "{\"contributionProfile\":{\"contributionAreas\":[\"FOOTBALL\"],"
                            + "\"otherContributionAreas\":[\"Synthetic event cooking\"]}}"))))
    @GetMapping("/{memberId}/contribution-profile")
    public ResponseEntity<MemberInformationRDTO.ContributionProfileResponse> contributionProfile(
            @PathVariable UUID memberId) {
        MemberInformation.Versioned<MemberInformationRDTO.ContributionProfileResponse> result =
                memberInformation.versionedContributionProfile(memberId);
        return ResponseEntity.ok().eTag(memberInformation.etag(result.version())).body(result.body());
    }

    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.MEMBER_INFORMATION_GET + "')")
    @Operation(operationId = "getAnnualMemberInformation", summary = "Get protected annual Member information",
            responses = @ApiResponse(responseCode = "200", content = @Content(
                    schema = @Schema(implementation = AnnualMemberInformationRDTO.class),
                    examples = @ExampleObject(value = "{\"id\":\"01960000-0002-7000-8000-000000000001\","
                            + "\"surveyCycle\":2026,\"submittedAt\":null,\"occupations\":{\"values\":[\"WORK\"],\"details\":null},"
                            + "\"healthCondition\":{\"status\":\"NO\",\"details\":null},"
                            + "\"religiousVocationConsidered\":\"NO\",\"massAttendanceFrequency\":\"WEEKLY\","
                            + "\"saturdayOratorioImpediment\":{\"status\":\"NO\",\"details\":null},"
                            + "\"formationAndMeetingInterests\":null,\"coordinationInterest\":\"NO\","
                            + "\"additionalComments\":null,\"oratorioActivitySuggestions\":null,"
                            + "\"instagramPostSuggestions\":null}"))))
    @GetMapping("/{memberId}/annual-information/{surveyCycle}")
    public ResponseEntity<AnnualMemberInformationRDTO> annualInformation(@PathVariable UUID memberId,
                                                                         @PathVariable int surveyCycle) {
        return ResponseEntity.ok(annualMemberInformation.get(memberId, surveyCycle));
    }

    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.MEMBER_MANAGE + "')")
    @Operation(operationId = "updateMemberProfile", summary = "Replace the Member core profile")
    @ApiResponse(responseCode = "204", description = "Member profile replaced",
            headers = @Header(name = HttpHeaders.ETAG, schema = @Schema(type = "string")))
    @PutMapping("/{memberId}")
    public ResponseEntity<Void> updateMember(@PathVariable UUID memberId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestBody @Valid MemberInformationDTO.Core dto) {
        return ResponseEntity.noContent().eTag(memberInformation.updateCore(memberId, ifMatch, dto)).build();
    }

    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.MEMBER_MANAGE + "')")
    @Operation(operationId = "updateMemberGamEntryDate", summary = "Replace the Member GAM entry date")
    @ApiResponse(responseCode = "204", description = "GAM entry date replaced",
            headers = @Header(name = HttpHeaders.ETAG, schema = @Schema(type = "string")))
    @PutMapping("/{memberId}/gam-entry-date")
    public ResponseEntity<Void> updateGamEntryDate(@PathVariable UUID memberId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestBody @Valid MemberInformationDTO.GamEntryDate dto) {
        return ResponseEntity.noContent().eTag(memberInformation.updateGamEntryDate(memberId, ifMatch, dto)).build();
    }

    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.MEMBER_MANAGE + "')")
    @Operation(operationId = "updateMemberDietaryRestriction", summary = "Replace the Member dietary restriction")
    @ApiResponse(responseCode = "204", description = "Dietary restriction replaced",
            headers = @Header(name = HttpHeaders.ETAG, schema = @Schema(type = "string")))
    @PutMapping("/{memberId}/dietary-restriction")
    public ResponseEntity<Void> updateDietaryRestriction(@PathVariable UUID memberId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestBody @Valid MemberInformationDTO.DietaryRestriction dto) {
        return ResponseEntity.noContent().eTag(memberInformation.updateDietaryRestriction(memberId, ifMatch, dto)).build();
    }

    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.MEMBER_MANAGE + "')")
    @Operation(operationId = "updateMemberExperiences", summary = "Replace all Member experience statuses")
    @ApiResponse(responseCode = "204", description = "Experiences replaced",
            headers = @Header(name = HttpHeaders.ETAG, schema = @Schema(type = "string")))
    @PutMapping("/{memberId}/experiences")
    public ResponseEntity<Void> updateExperiences(@PathVariable UUID memberId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestBody @Valid MemberInformationDTO.Experiences dto) {
        return ResponseEntity.noContent().eTag(memberInformation.updateExperiences(memberId, ifMatch, dto)).build();
    }

    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.MEMBER_MANAGE + "')")
    @Operation(operationId = "updateMemberSacraments", summary = "Replace all Member sacrament statuses")
    @ApiResponse(responseCode = "204", description = "Sacraments replaced",
            headers = @Header(name = HttpHeaders.ETAG, schema = @Schema(type = "string")))
    @PutMapping("/{memberId}/sacraments")
    public ResponseEntity<Void> updateSacraments(@PathVariable UUID memberId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestBody @Valid MemberInformationDTO.Sacraments dto) {
        return ResponseEntity.noContent().eTag(memberInformation.updateSacraments(memberId, ifMatch, dto)).build();
    }

    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.MEMBER_MANAGE + "')")
    @Operation(operationId = "updateMemberContributionProfile", summary = "Replace the Member contribution profile")
    @ApiResponse(responseCode = "204", description = "Contribution profile replaced",
            headers = @Header(name = HttpHeaders.ETAG, schema = @Schema(type = "string")))
    @PutMapping("/{memberId}/contribution-profile")
    public ResponseEntity<Void> updateContributionProfile(@PathVariable UUID memberId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestBody @Valid MemberInformationDTO.ContributionProfile dto) {
        return ResponseEntity.noContent().eTag(memberInformation.updateContributionProfile(memberId, ifMatch, dto)).build();
    }

    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.MEMBER_SEARCH + "')")
    @Operation(operationId = "searchMembers")
    @PostMapping("/search")
    public ResponseEntity<PagedResponse<MemberRDTO>> searchMembers(@RequestBody @Valid SearchDTO searchDTO,
                                                                     Pageable pageable) {

        return ResponseEntity.ok(PagedResponse.from(searchMembers.search(searchDTO, pageable)));
    }

    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.COORDINATOR_MANAGE + "')")
    @Operation(operationId = "grantCoordinator", summary = "Grant Coordinator designation")
    @ApiResponse(responseCode = "204", description = "Coordinator designation granted")
    @PatchMapping("/{memberId}/coordinator/grant")
    public ResponseEntity<Void> grantCoordinator(@PathVariable UUID memberId,
                                                  @RequestBody @Valid CoordinatorTransitionDTO dto) {
        activation.grantCoordinator(memberId, dto.reason());
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.COORDINATOR_MANAGE + "')")
    @Operation(operationId = "revokeCoordinator", summary = "Revoke Coordinator designation")
    @ApiResponse(responseCode = "204", description = "Coordinator designation revoked")
    @PatchMapping("/{memberId}/coordinator/revoke")
    public ResponseEntity<Void> revokeCoordinator(@PathVariable UUID memberId,
                                                   @RequestBody @Valid CoordinatorTransitionDTO dto) {
        activation.revokeCoordinator(memberId, dto.reason());
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.ORATORIO_COORD_MANAGE + "')")
    @Operation(operationId = "grantOratorioCoordinator", summary = "Grant Oratorio Coordinator designation")
    @ApiResponse(responseCode = "204", description = "Oratorio Coordinator designation granted")
    @PatchMapping("/{memberId}/oratorio-coordinator/grant")
    public ResponseEntity<Void> grantOratorioCoordinator(
            @PathVariable UUID memberId,
            @RequestBody @Valid CoordinatorTransitionDTO dto
    ) {
        activation.grantOratorioCoordinator(memberId, dto.reason());
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.ORATORIO_COORD_MANAGE + "')")
    @Operation(operationId = "revokeOratorioCoordinator", summary = "Revoke Oratorio Coordinator designation")
    @ApiResponse(responseCode = "204", description = "Oratorio Coordinator designation revoked")
    @PatchMapping("/{memberId}/oratorio-coordinator/revoke")
    public ResponseEntity<Void> revokeOratorioCoordinator(
            @PathVariable UUID memberId,
            @RequestBody @Valid CoordinatorTransitionDTO dto
    ) {
        activation.revokeOratorioCoordinator(memberId, dto.reason());
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.MEMBER_ACCOUNT_LINK + "')")
    @Operation(
            operationId = "linkMemberAccount",
            summary = "Link an existing Account to an Account-less Member",
            description = "Links one eligible existing Account to one eligible Account-less Member and projects "
                    + "the Member lifecycle Role. If the Account has a pending Membership Solicitation for an "
                    + "existing Member, a Coordinator must first reject that solicitation after human review; "
                    + "approving it would create a second Member instead of linking the existing one."
    )
    @ApiResponse(responseCode = "204", description = "Member Account linked")
    @PatchMapping("/{memberId}/account/link")
    public ResponseEntity<Void> linkAccount(
            @PathVariable UUID memberId,
            @RequestBody @Valid LinkMemberAccountDTO dto
    ) {
        activation.linkAccount(memberId, dto);
        return ResponseEntity.noContent().build();
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

        return ResponseEntity.ok(PagedResponse.from(getPresence.allByMemberOrdered(memberId, pageable)));
    }
}
