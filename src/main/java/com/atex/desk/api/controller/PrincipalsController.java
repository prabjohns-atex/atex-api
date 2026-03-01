package com.atex.desk.api.controller;

import com.atex.desk.api.auth.AuthFilter;
import com.atex.desk.api.dto.ErrorResponseDto;
import com.atex.desk.api.dto.GroupDto;
import com.atex.desk.api.dto.PrincipalDto;
import com.atex.desk.api.entity.AppGroup;
import com.atex.desk.api.entity.AppGroupMember;
import com.atex.desk.api.entity.AppGroupMemberId;
import com.atex.desk.api.entity.AppGroupOwner;
import com.atex.desk.api.entity.AppGroupOwnerId;
import com.atex.desk.api.repository.AppGroupMemberRepository;
import com.atex.desk.api.repository.AppGroupOwnerRepository;
import com.atex.desk.api.repository.AppGroupRepository;
import com.atex.desk.api.repository.AppUserRepository;
import com.atex.desk.api.entity.AppUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/principals")
@Tag(name = "Principals")
public class PrincipalsController
{
    private final AppUserRepository userRepository;
    private final AppGroupRepository groupRepository;
    private final AppGroupMemberRepository groupMemberRepository;
    private final AppGroupOwnerRepository groupOwnerRepository;

    public PrincipalsController(AppUserRepository userRepository,
                                AppGroupRepository groupRepository,
                                AppGroupMemberRepository groupMemberRepository,
                                AppGroupOwnerRepository groupOwnerRepository)
    {
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.groupOwnerRepository = groupOwnerRepository;
    }

    // ======== User endpoints ========

    @GetMapping("/users/me")
    @Operation(summary = "Get current authenticated user")
    @ApiResponse(responseCode = "200", description = "Current user info",
                 content = @Content(schema = @Schema(implementation = PrincipalDto.class)))
    @ApiResponse(responseCode = "401", description = "Not authenticated",
                 content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    public ResponseEntity<?> getCurrentUser(HttpServletRequest request)
    {
        String loginName = resolveUserId(request);
        if (loginName == null)
        {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponseDto("UNAUTHORIZED", "No authenticated user"));
        }

        return userRepository.findByLoginName(loginName)
            .<ResponseEntity<?>>map(user -> ResponseEntity.ok(toUserDto(user)))
            .orElseGet(() -> notFound("User not found: " + loginName));
    }

    @GetMapping("/users")
    @Operation(summary = "List all users")
    public List<PrincipalDto> getUsers(
        @Parameter(description = "Include group memberships for each user")
        @RequestParam(value = "addGroupsToUsers", defaultValue = "false") boolean addGroupsToUsers)
    {
        List<AppUser> users = userRepository.findAll();
        return users.stream()
            .filter(AppUser::isActive)
            .map(u -> {
                PrincipalDto dto = toUserDto(u);
                if (addGroupsToUsers)
                {
                    List<AppGroupMember> memberships = groupMemberRepository.findByPrincipalId(u.getLoginName());
                    List<String> groupNames = memberships.stream()
                        .map(m -> groupRepository.findById(m.getGroupId())
                            .map(AppGroup::getName)
                            .orElse(null))
                        .filter(n -> n != null)
                        .toList();
                    dto.setGroupList(groupNames);
                }
                return dto;
            })
            .toList();
    }

    @GetMapping("/users/{userId}")
    @Operation(summary = "Get user by login name")
    @ApiResponse(responseCode = "200", description = "User found",
                 content = @Content(schema = @Schema(implementation = PrincipalDto.class)))
    @ApiResponse(responseCode = "404", description = "User not found",
                 content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    public ResponseEntity<?> getUser(
        @Parameter(description = "User login name") @PathVariable String userId)
    {
        return userRepository.findByLoginName(userId)
            .<ResponseEntity<?>>map(user -> ResponseEntity.ok(toUserDto(user)))
            .orElseGet(() -> notFound("User not found: " + userId));
    }

    // ======== Group endpoints ========

    @GetMapping("/groups")
    @Operation(summary = "List all groups")
    public List<GroupDto> getGroups()
    {
        return groupRepository.findAll().stream()
            .map(this::toGroupDto)
            .toList();
    }

    @GetMapping("/groups/{groupId}")
    @Operation(summary = "Get group by ID")
    @ApiResponse(responseCode = "200", description = "Group found",
                 content = @Content(schema = @Schema(implementation = GroupDto.class)))
    @ApiResponse(responseCode = "404", description = "Group not found",
                 content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    public ResponseEntity<?> getGroup(
        @Parameter(description = "Group ID") @PathVariable Integer groupId,
        @Parameter(description = "Include member principal IDs")
        @RequestParam(value = "members", defaultValue = "false") boolean members)
    {
        return groupRepository.findById(groupId)
            .<ResponseEntity<?>>map(g -> {
                GroupDto dto = toGroupDto(g);
                if (members)
                {
                    List<String> memberIds = groupMemberRepository.findByGroupId(g.getGroupId()).stream()
                        .map(AppGroupMember::getPrincipalId)
                        .toList();
                    dto.setMembers(memberIds);
                }
                return ResponseEntity.ok(dto);
            })
            .orElseGet(() -> notFound("Group not found: " + groupId));
    }

    @PostMapping("/groups")
    @Operation(summary = "Create a new group")
    @ApiResponse(responseCode = "201", description = "Group created",
                 content = @Content(schema = @Schema(implementation = GroupDto.class)))
    public ResponseEntity<?> createGroup(@RequestBody Map<String, String> body,
                                         HttpServletRequest request)
    {
        String name = body.get("name");
        if (name == null || name.isBlank())
        {
            return ResponseEntity.badRequest()
                .body(new ErrorResponseDto("BAD_REQUEST", "Group name is required"));
        }
        if (groupRepository.findByName(name).isPresent())
        {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponseDto("CONFLICT", "Group already exists: " + name));
        }
        String currentUser = resolveUserId(request);
        AppGroup group = new AppGroup();
        group.setName(name);
        group.setCreationTime((int) (System.currentTimeMillis() / 1000));
        group.setFirstOwnerId(currentUser);
        group = groupRepository.save(group);

        // Add creator as owner
        if (currentUser != null)
        {
            AppGroupOwner owner = new AppGroupOwner();
            owner.setGroupId(group.getGroupId());
            owner.setPrincipalId(currentUser);
            groupOwnerRepository.save(owner);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(toGroupDto(group));
    }

    @PutMapping("/groups/{groupId}")
    @Operation(summary = "Update group name")
    @ApiResponse(responseCode = "200", description = "Group updated",
                 content = @Content(schema = @Schema(implementation = GroupDto.class)))
    @ApiResponse(responseCode = "404", description = "Group not found",
                 content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    public ResponseEntity<?> updateGroup(
        @Parameter(description = "Group ID") @PathVariable Integer groupId,
        @RequestBody Map<String, String> body)
    {
        return groupRepository.findById(groupId)
            .<ResponseEntity<?>>map(g -> {
                String name = body.get("name");
                if (name != null && !name.isBlank())
                {
                    g.setName(name);
                    g = groupRepository.save(g);
                }
                return ResponseEntity.ok(toGroupDto(g));
            })
            .orElseGet(() -> notFound("Group not found: " + groupId));
    }

    @DeleteMapping("/groups/{groupId}")
    @Transactional
    @Operation(summary = "Delete a group and its memberships")
    @ApiResponse(responseCode = "204", description = "Group deleted")
    @ApiResponse(responseCode = "404", description = "Group not found",
                 content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    public ResponseEntity<?> deleteGroup(
        @Parameter(description = "Group ID") @PathVariable Integer groupId)
    {
        if (!groupRepository.existsById(groupId))
        {
            return notFound("Group not found: " + groupId);
        }
        // Delete memberships and owners first, then group
        List<AppGroupMember> members = groupMemberRepository.findByGroupId(groupId);
        groupMemberRepository.deleteAll(members);
        List<AppGroupOwner> owners = groupOwnerRepository.findByGroupId(groupId);
        groupOwnerRepository.deleteAll(owners);
        groupRepository.deleteById(groupId);
        return ResponseEntity.noContent().build();
    }

    // ======== Membership endpoints ========

    @PostMapping("/groups/{groupId}/members")
    @Operation(summary = "Add a member to a group")
    @ApiResponse(responseCode = "200", description = "Member added")
    @ApiResponse(responseCode = "404", description = "Group or user not found",
                 content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    public ResponseEntity<?> addMember(
        @Parameter(description = "Group ID") @PathVariable Integer groupId,
        @RequestBody Map<String, String> body)
    {
        String principalId = body.get("principalId");
        if (principalId == null || principalId.isBlank())
        {
            return ResponseEntity.badRequest()
                .body(new ErrorResponseDto("BAD_REQUEST", "principalId is required"));
        }
        if (!groupRepository.existsById(groupId))
        {
            return notFound("Group not found: " + groupId);
        }
        AppGroupMemberId pk = new AppGroupMemberId(groupId, principalId);
        if (!groupMemberRepository.existsById(pk))
        {
            AppGroupMember member = new AppGroupMember();
            member.setGroupId(groupId);
            member.setPrincipalId(principalId);
            groupMemberRepository.save(member);
        }
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/groups/{groupId}/members/{principalId}")
    @Transactional
    @Operation(summary = "Remove a member from a group")
    @ApiResponse(responseCode = "204", description = "Member removed")
    public ResponseEntity<?> removeMember(
        @Parameter(description = "Group ID") @PathVariable Integer groupId,
        @Parameter(description = "Principal ID") @PathVariable String principalId)
    {
        groupMemberRepository.deleteByGroupIdAndPrincipalId(groupId, principalId);
        return ResponseEntity.noContent().build();
    }

    // ======== Helpers ========

    private PrincipalDto toUserDto(AppUser user)
    {
        PrincipalDto dto = new PrincipalDto();
        dto.setUserId(user.getLoginName());
        dto.setUsername(user.getLoginName());
        if (user.getRegTime() > 0)
        {
            dto.setCreatedAt(String.valueOf((long) user.getRegTime() * 1000));
        }
        return dto;
    }

    private GroupDto toGroupDto(AppGroup group)
    {
        GroupDto dto = new GroupDto();
        dto.setGroupId(group.getGroupId().toString());
        dto.setName(group.getName());
        if (group.getCreationTime() > 0)
        {
            dto.setCreatedAt(String.valueOf((long) group.getCreationTime() * 1000));
        }
        return dto;
    }

    private String resolveUserId(HttpServletRequest request)
    {
        Object user = request.getAttribute(AuthFilter.USER_ATTRIBUTE);
        return user != null ? user.toString() : null;
    }

    private ResponseEntity<ErrorResponseDto> notFound(String message)
    {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponseDto("NOT_FOUND", message));
    }
}
