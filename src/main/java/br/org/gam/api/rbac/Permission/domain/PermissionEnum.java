package br.org.gam.api.rbac.Permission.domain;

import lombok.Getter;

@Getter
public enum PermissionEnum {
    MEMBER_GET(Code.MEMBER_GET, "Permite visualizar membros ativos"),
    MEMBER_SEARCH(Code.MEMBER_SEARCH, "Permite realizar uma busca por membros"),
    MEMBER_ACTIVATION(Code.MEMBER_ACTIVATION, "Permite ativar e desativar membros"),
    MEMBER_GET_NON_ACTIVE(Code.MEMBER_GET_NON_ACTIVE, "Permite visualizar membros não ativos"),
    MEMBER_MANAGE(Code.MEMBER_MANAGE, "Permite gerenciar membros"),


    ACCOUNT_GET(Code.ACCOUNT_GET, "Permite visualizar contas"),
    ACCOUNT_SEARCH(Code.ACCOUNT_SEARCH, "Permite realizar uma busca por contas"),


    EVENT_CREATE(Code.EVENT_CREATE, "Permite criar eventos"),
    EVENT_SEARCH(Code.EVENT_SEARCH, "Permite realizar uma busca por eventos"),
    EVENT_GET_PRESENCES(Code.EVENT_GET_PRESENCES, "Permite visualizar as presenças de um evento"),
    EVENT_GET_S(Code.EVENT_GET_S, "Permite visualizar eventos do tipo S"),
    EVENT_MANAGE(Code.EVENT_MANAGE, "Permite gerenciar eventos"),


    PRESENCES_SEARCH(Code.PRESENCES_SEARCH, "Permite realizar uma busca por presenças");

    private final String code;
    private final String description;

    PermissionEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static class Code {
        public static final String MEMBER_GET = "MEMBER_GET";
        public static final String MEMBER_SEARCH = "MEMBER_SEARCH";
        public static final String MEMBER_ACTIVATION = "MEMBER_ACTIVATION";
        public static final String MEMBER_GET_NON_ACTIVE = "MEMBER_GET_NON_ACTIVE";
        public static final String MEMBER_MANAGE = "MEMBER_MANAGE";


        public static final String ACCOUNT_GET = "ACCOUNT_GET";
        public static final String ACCOUNT_SEARCH = "ACCOUNT_SEARCH";


        public static final String EVENT_CREATE = "EVENT_CREATE";
        public static final String EVENT_SEARCH = "EVENT_SEARCH";
        public static final String EVENT_GET_PRESENCES = "EVENT_GET_PRESENCES";
        public static final String EVENT_GET_S = "EVENT_GET_S";
        public static final String EVENT_MANAGE = "EVENT_MANAGE";


        public static final String PRESENCES_SEARCH = "PRESENCES_SEARCH";
    }
}