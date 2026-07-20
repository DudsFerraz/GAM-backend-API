package br.org.gam.api.rbac.permission.domain;

import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;

@Getter
public enum PermissionEnum {
    MEMBER_GET(Code.MEMBER_GET, "View members", "Allows viewing active members"),
    MEMBER_SEARCH(Code.MEMBER_SEARCH, "Search members", "Allows searching members"),
    MEMBER_ACTIVATION(Code.MEMBER_ACTIVATION, "Activate members", "Allows activating and deactivating members"),
    MEMBER_GET_NON_ACTIVE(Code.MEMBER_GET_NON_ACTIVE, "View inactive members", "Allows viewing non-active members"),
    MEMBER_MANAGE(Code.MEMBER_MANAGE, "Manage members", "Allows managing members"),
    COORDINATOR_MANAGE(Code.COORDINATOR_MANAGE, "Manage coordinators", "Allows granting and revoking Coordinator designation"),

    ACCOUNT_GET(Code.ACCOUNT_GET, "View accounts", "Allows viewing accounts"),
    ACCOUNT_SEARCH(Code.ACCOUNT_SEARCH, "Search accounts", "Allows searching accounts"),
    ACCOUNT_ROLE_MANAGE(Code.ACCOUNT_ROLE_MANAGE, "Manage account roles", "Allows adding and removing account roles"),

    EVENT_CREATE(Code.EVENT_CREATE, "Create events", "Allows creating events"),
    EVENT_SEARCH(Code.EVENT_SEARCH, "Search events", "Allows searching events"),
    EVENT_GET_PRESENCES(Code.EVENT_GET_PRESENCES, "View event presences", "Allows viewing presences for an event"),
    EVENT_GET_MEMBER(Code.EVENT_GET_MEMBER, "View member events", "Allows viewing events requiring member access"),
    EVENT_GET_COORD(Code.EVENT_GET_COORD, "View coordinator events", "Allows viewing events requiring coordinator access"),
    EVENT_MANAGE(Code.EVENT_MANAGE, "Manage events", "Allows managing events"),

    GAM_LOCATION_GET(Code.GAM_LOCATION_GET, "View GAM locations", "Allows directly viewing active GamLocation records"),
    GAM_LOCATION_CREATE(Code.GAM_LOCATION_CREATE, "Create GAM locations", "Allows creating GamLocation records"),
    GAM_LOCATION_MANAGE(Code.GAM_LOCATION_MANAGE, "Manage GAM locations", "Allows updating and removing GamLocation records"),

    PRESENCES_SEARCH(Code.PRESENCES_SEARCH, "Search presences", "Allows searching presences"),
    PRESENCE_REGISTER(Code.PRESENCE_REGISTER, "Register presences", "Allows recording Member attendance at Events"),
    PRESENCE_EDIT(Code.PRESENCE_EDIT, "Edit presences", "Allows editing observations on Member attendance records"),
    PRESENCE_REMOVE(Code.PRESENCE_REMOVE, "Remove presences", "Allows removing mistaken Member attendance records"),

    ROLE_GET(Code.ROLE_GET, "View roles", "Allows reading role catalog entries"),
    PERMISSION_GET(Code.PERMISSION_GET, "View permissions", "Allows reading permission catalog entries");

    private final String code;
    private final String label;
    private final String description;

    PermissionEnum(String code, String label, String description) {
        this.code = code;
        this.label = label;
        this.description = description;
    }

    public static Optional<PermissionEnum> fromCode(String code) {
        return Arrays.stream(values())
                .filter(permission -> permission.code.equals(code))
                .findFirst();
    }

    public static class Code {
        public static final String MEMBER_GET = "MEMBER_GET";
        public static final String MEMBER_SEARCH = "MEMBER_SEARCH";
        public static final String MEMBER_ACTIVATION = "MEMBER_ACTIVATION";
        public static final String MEMBER_GET_NON_ACTIVE = "MEMBER_GET_NON_ACTIVE";
        public static final String MEMBER_MANAGE = "MEMBER_MANAGE";
        public static final String COORDINATOR_MANAGE = "COORDINATOR_MANAGE";

        public static final String ACCOUNT_GET = "ACCOUNT_GET";
        public static final String ACCOUNT_SEARCH = "ACCOUNT_SEARCH";
        public static final String ACCOUNT_ROLE_MANAGE = "ACCOUNT_ROLE_MANAGE";

        public static final String EVENT_CREATE = "EVENT_CREATE";
        public static final String EVENT_SEARCH = "EVENT_SEARCH";
        public static final String EVENT_GET_PRESENCES = "EVENT_GET_PRESENCES";
        public static final String EVENT_GET_MEMBER = "EVENT_GET_MEMBER";
        public static final String EVENT_GET_COORD = "EVENT_GET_COORD";
        public static final String EVENT_MANAGE = "EVENT_MANAGE";

        public static final String GAM_LOCATION_GET = "GAM_LOCATION_GET";
        public static final String GAM_LOCATION_CREATE = "GAM_LOCATION_CREATE";
        public static final String GAM_LOCATION_MANAGE = "GAM_LOCATION_MANAGE";

        public static final String PRESENCES_SEARCH = "PRESENCES_SEARCH";
        public static final String PRESENCE_REGISTER = "PRESENCE_REGISTER";
        public static final String PRESENCE_EDIT = "PRESENCE_EDIT";
        public static final String PRESENCE_REMOVE = "PRESENCE_REMOVE";

        public static final String ROLE_GET = "ROLE_GET";
        public static final String PERMISSION_GET = "PERMISSION_GET";
    }
}
