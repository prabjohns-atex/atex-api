package com.atex.desk.api.controller;

import com.atex.desk.api.auth.AuthFilter;
import com.atex.desk.api.dto.ContentResultDto;
import com.atex.desk.api.dto.ErrorResponseDto;
import com.atex.desk.api.dto.GroupDto;
import com.atex.desk.api.dto.GroupRefDto;
import com.atex.desk.api.dto.PrincipalDto;
import com.atex.desk.api.dto.UserMeDto;
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
import com.atex.desk.api.service.ContentService;
import com.atex.desk.api.service.ObjectCacheService;
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

import jakarta.annotation.PostConstruct;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/principals")
@Tag(name = "Principals")
public class PrincipalsController
{
    private static final String CACHE_USER_ME = "userMe";

    private final AppUserRepository userRepository;
    private final AppGroupRepository groupRepository;
    private final AppGroupMemberRepository groupMemberRepository;
    private final AppGroupOwnerRepository groupOwnerRepository;
    private final ContentService contentService;
    private final ObjectCacheService cacheService;

    public PrincipalsController(AppUserRepository userRepository,
                                AppGroupRepository groupRepository,
                                AppGroupMemberRepository groupMemberRepository,
                                AppGroupOwnerRepository groupOwnerRepository,
                                ContentService contentService,
                                ObjectCacheService cacheService)
    {
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.groupOwnerRepository = groupOwnerRepository;
        this.contentService = contentService;
        this.cacheService = cacheService;
    }

    @PostConstruct
    void initCaches()
    {
        cacheService.configure(CACHE_USER_ME, 5 * 60 * 1000L, 200); // 5 min TTL, 200 entries
    }

    // ======== User endpoints ========

    @GetMapping("/users/me")
    @Operation(summary = "Get current authenticated user")
    @ApiResponse(responseCode = "200", description = "Current user info",
                 content = @Content(schema = @Schema(implementation = UserMeDto.class)))
    @ApiResponse(responseCode = "401", description = "Not authenticated",
                 content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    public ResponseEntity<?> getCurrentUser(HttpServletRequest request)
    {
        String loginName = resolveUserId(request);
        if (loginName == null)
        {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponseDto(HttpStatus.UNAUTHORIZED, "No authenticated user"));
        }

        // Cache the entire response by loginName — avoids DB query + content enrichment
        UserMeDto cached = cacheService.get(CACHE_USER_ME, loginName);
        if (cached != null) return ResponseEntity.ok(cached);

        return userRepository.findByLoginName(loginName)
            .<ResponseEntity<?>>map(user -> {
                UserMeDto dto = buildUserMeDto(user);
                cacheService.put(CACHE_USER_ME, loginName, dto);
                return ResponseEntity.ok(dto);
            })
            .orElseGet(() -> notFound("User not found: " + loginName));
    }

    @GetMapping("/users")
    @Operation(summary = "List all users")
    public List<PrincipalDto> getUsers(
        @Parameter(description = "Include group memberships for each user (ignored, kept for backward compat)")
        @RequestParam(value = "addGroupsToUsers", defaultValue = "false") boolean addGroupsToUsers)
    {
        List<AppUser> users = userRepository.findAll();
        return users.stream()
            .filter(AppUser::isActive)
            .map(this::toUserDto)
            .toList();
    }

    @GetMapping("/users/{userId}")
    @Operation(summary = "Get user by login name")
    @ApiResponse(responseCode = "200", description = "User found",
                 content = @Content(schema = @Schema(implementation = UserMeDto.class)))
    @ApiResponse(responseCode = "404", description = "User not found",
                 content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    public ResponseEntity<?> getUser(
        @Parameter(description = "User login name") @PathVariable String userId)
    {
        UserMeDto cached = cacheService.get(CACHE_USER_ME, userId);
        if (cached != null) return ResponseEntity.ok(cached);

        // Try loginName first, then fall back to principalId (numeric ID)
        Optional<AppUser> userOpt = userRepository.findByLoginName(userId);
        if (userOpt.isEmpty())
        {
            userOpt = userRepository.findByPrincipalId(userId);
        }
        return userOpt
            .<ResponseEntity<?>>map(user -> {
                UserMeDto dto = buildUserMeDto(user);
                cacheService.put(CACHE_USER_ME, userId, dto);
                return ResponseEntity.ok(dto);
            })
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
                .body(new ErrorResponseDto(HttpStatus.BAD_REQUEST, "Group name is required"));
        }
        if (groupRepository.findByName(name).isPresent())
        {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponseDto(HttpStatus.CONFLICT, "Group already exists: " + name));
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
                .body(new ErrorResponseDto(HttpStatus.BAD_REQUEST, "principalId is required"));
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
        String principalId = effectivePrincipalId(user);
        dto.setId(principalId);
        dto.setName(user.getLoginName());
        dto.setPrincipalId(principalId);
        dto.setCmUser(user.isCmUser());
        dto.setLdapUser(user.isLdap());
        dto.setRemoteUser(user.isRemote());
        return dto;
    }

    private UserMeDto buildUserMeDto(AppUser user)
    {
        String principalId = effectivePrincipalId(user);

        UserMeDto dto = new UserMeDto();
        dto.setLoginName(user.getLoginName());
        dto.setFirstName(user.getLoginName());
        dto.setLastName("");
        dto.setId(parseIntOrHash(principalId));
        dto.setUserId(principalId);
        dto.setCmUser(user.isCmUser());
        dto.setLdapUser(user.isLdap());
        dto.setRemoteUser(user.isRemote());

        // Enrich from migrated user content (UserDataBean) if available
        Map<String, Object> userData = enrichFromUserContent(principalId);
        if (userData != null)
        {
            Object fn = userData.get("firstname");
            if (fn != null) dto.setFirstName(fn.toString());
            Object sn = userData.get("surname");
            if (sn != null) dto.setLastName(sn.toString());

            // workingSites is a list of {id, name} objects — extract the IDs
            Object ws = userData.get("workingSites");
            if (ws instanceof List<?> wsList && !wsList.isEmpty())
            {
                List<String> siteIds = new ArrayList<>();
                for (Object item : wsList)
                {
                    if (item instanceof Map<?, ?> siteMap)
                    {
                        Object siteId = siteMap.get("id");
                        if (siteId != null) siteIds.add(siteId.toString());
                    }
                }
                dto.setWorkingSites(siteIds);
            }

            // Pass through relations from content
            Object relations = userData.get("relations");
            if (relations instanceof Map<?, ?> relMap)
            {
                dto.setUserData(Map.of("relations", relMap));
            }
        }

        if (dto.getWorkingSites() == null) dto.setWorkingSites(Collections.emptyList());
        if (dto.getUserData() == null) dto.setUserData(Map.of("relations", Collections.emptyMap()));

        // Look up group memberships — batch query instead of N+1
        List<AppGroupMember> memberships = groupMemberRepository.findByPrincipalId(principalId);
        List<Integer> groupIds = memberships.stream().map(AppGroupMember::getGroupId).toList();
        Map<Integer, AppGroup> groupMap = groupRepository.findAllById(groupIds).stream()
            .collect(java.util.stream.Collectors.toMap(AppGroup::getGroupId, g -> g));
        List<GroupRefDto> groups = groupIds.stream()
            .map(groupMap::get)
            .filter(g -> g != null)
            .map(g -> {
                GroupRefDto ref = new GroupRefDto();
                String groupIdStr = "group:" + g.getGroupId();
                ref.setId(groupIdStr);
                ref.setName(g.getName());
                ref.setPrincipalId(groupIdStr);
                return ref;
            })
            .toList();
        dto.setGroups(groups);

        dto.setHomeDepartmentId(null);

        return dto;
    }

    /**
     * Fetch the migrated user content by principalId (stored as externalId alias)
     * and return the contentData map, or null if not found.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> enrichFromUserContent(String principalId)
    {
        try
        {
            var contentId = contentService.resolveExternalId(principalId);
            if (contentId.isEmpty()) return null;

            String[] parts = contentService.parseContentId(contentId.get());
            var versionedId = contentService.resolve(parts[0], parts[1]);
            if (versionedId.isEmpty()) return null;

            String[] vParts = contentService.parseContentId(versionedId.get());
            var result = contentService.getContent(vParts[0], vParts[1], vParts[2]);
            if (result.isEmpty()) return null;

            var aspects = result.get().getAspects();
            if (aspects == null) return null;
            var contentData = aspects.get("contentData");
            if (contentData == null) return null;
            return contentData.getData();
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /**
     * Return the user's principalId from the registeredusers table,
     * falling back to the login name if not yet populated.
     */
    private String effectivePrincipalId(AppUser user)
    {
        return user.getPrincipalId() != null ? user.getPrincipalId() : user.getLoginName();
    }

    private int parseIntOrHash(String value)
    {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return Math.abs(value.hashCode());
        }
    }

    private GroupDto toGroupDto(AppGroup group)
    {
        GroupDto dto = new GroupDto();
        String gid = "group:" + group.getGroupId();
        dto.setId(gid);
        dto.setPrincipalId(gid);
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
            .body(new ErrorResponseDto(HttpStatus.NOT_FOUND, message));
    }
}
