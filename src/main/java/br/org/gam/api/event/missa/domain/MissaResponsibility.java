package br.org.gam.api.event.missa.domain;

public enum MissaResponsibility {
    COMENTARIOS(true),
    PRIMEIRA_LEITURA(true),
    SALMO(true),
    SEGUNDA_LEITURA(true),
    PRECES(true),
    ACOLHIDA(false),
    BANDA(false);

    private final boolean singleMember;

    MissaResponsibility(boolean singleMember) {
        this.singleMember = singleMember;
    }

    public boolean singleMember() {
        return singleMember;
    }
}
