CREATE TYPE member_status_enum AS ENUM ('ACTIVE', 'INACTIVE');
CREATE TYPE member_information_status_enum AS ENUM ('YES', 'NO', 'NOT_INFORMED');
CREATE TYPE member_experience_type_enum AS ENUM ('JORNADA_MISSIONARIA', 'CURSO_DE_LIDERANCA', 'PASCOA_JUVENIL', 'ACAMPABOSCO');
CREATE TYPE member_sacrament_type_enum AS ENUM ('BATISMO', 'PRIMEIRA_COMUNHAO', 'CRISMA');
CREATE TYPE member_contribution_area_enum AS ENUM (
    'GAME_REFEREE', 'CRAFTS', 'MUSIC', 'PRAYER_LEADERSHIP', 'BOA_TARDE_STORYTELLING', 'DANCE',
    'BALLOON_SCULPTURE', 'FOOTBALL', 'VOLLEYBALL', 'BASKETBALL', 'HANDBALL', 'PHOTOGRAPHY_AND_VIDEO',
    'PUBLIC_READING', 'FACE_PAINTING', 'FIRST_AID', 'GINCANA_LEADERSHIP', 'TECHNOLOGY', 'TERERE'
);
CREATE TYPE member_occupation_enum AS ENUM ('WORK', 'UNIVERSITY', 'PREP_COURSE', 'OTHER');
CREATE TYPE member_mass_attendance_frequency_enum AS ENUM ('WEEKLY', 'THREE_TIMES_PER_MONTH', 'TWICE_PER_MONTH', 'MONTHLY', 'NOT_INFORMED');
CREATE TYPE member_coordination_interest_enum AS ENUM ('YES', 'NO', 'MAYBE', 'NOT_INFORMED');

CREATE TABLE member_information_import_batches (
    id UUID PRIMARY KEY,
    survey_cycle INTEGER NOT NULL CHECK (survey_cycle BETWEEN 2000 AND 9999),
    dataset_checksum VARCHAR(71) NOT NULL UNIQUE,
    imported_member_count INTEGER NOT NULL CHECK (imported_member_count >= 0),
    imported_response_count INTEGER NOT NULL CHECK (imported_response_count >= 0),
    executed_at TIMESTAMPTZ NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    CONSTRAINT check_member_information_import_checksum CHECK (dataset_checksum ~ '^sha256:[0-9a-f]{64}$')
);

CREATE TABLE members (
    id UUID PRIMARY KEY,
    account_id UUID,
    version BIGINT NOT NULL DEFAULT 0,
    first_name VARCHAR(255) NOT NULL,
    surname VARCHAR(255) NOT NULL,
    birth_date DATE NOT NULL,
    gam_entry_date DATE NOT NULL,
    residential_city VARCHAR(100) NOT NULL,
    phone_number VARCHAR(30) NOT NULL,
    contact_email VARCHAR(320) NOT NULL,
    dietary_restriction_status member_information_status_enum NOT NULL DEFAULT 'NOT_INFORMED',
    dietary_restriction_details VARCHAR(2000),
    import_batch_id UUID,
    status member_status_enum NOT NULL,

    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by UUID,
    deleted_at TIMESTAMPTZ,
    deleted_by UUID,

    CONSTRAINT fk_member_account
        FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE RESTRICT,
    CONSTRAINT fk_member_import_batch
        FOREIGN KEY (import_batch_id) REFERENCES member_information_import_batches(id) ON DELETE RESTRICT,
    CONSTRAINT fk_member_created_by
        FOREIGN KEY (created_by) REFERENCES accounts(id) ON DELETE SET NULL,
    CONSTRAINT fk_member_updated_by
        FOREIGN KEY (updated_by) REFERENCES accounts(id) ON DELETE SET NULL,
    CONSTRAINT fk_member_deleted_by
        FOREIGN KEY (deleted_by) REFERENCES accounts(id) ON DELETE SET NULL,
    CONSTRAINT check_members_deleted_attribution
        CHECK (deleted_by IS NULL OR deleted_at IS NOT NULL),
    CONSTRAINT check_members_city_size CHECK (char_length(residential_city) BETWEEN 1 AND 100),
    CONSTRAINT check_members_dietary_details CHECK (
        (dietary_restriction_status = 'YES' AND dietary_restriction_details IS NOT NULL
            AND char_length(dietary_restriction_details) BETWEEN 1 AND 2000)
        OR (dietary_restriction_status <> 'YES' AND dietary_restriction_details IS NULL)
    )
);

CREATE UNIQUE INDEX idx_members_account_id
    ON members (account_id);

CREATE TABLE member_experiences (
    member_id UUID NOT NULL REFERENCES members(id) ON DELETE CASCADE,
    experience_type member_experience_type_enum NOT NULL,
    status member_information_status_enum NOT NULL,
    PRIMARY KEY (member_id, experience_type)
);

CREATE TABLE member_sacraments (
    member_id UUID NOT NULL REFERENCES members(id) ON DELETE CASCADE,
    sacrament_type member_sacrament_type_enum NOT NULL,
    status member_information_status_enum NOT NULL,
    PRIMARY KEY (member_id, sacrament_type)
);

CREATE TABLE member_contribution_areas (
    member_id UUID NOT NULL REFERENCES members(id) ON DELETE CASCADE,
    contribution_area member_contribution_area_enum NOT NULL,
    PRIMARY KEY (member_id, contribution_area)
);

CREATE TABLE member_other_contribution_areas (
    member_id UUID NOT NULL REFERENCES members(id) ON DELETE CASCADE,
    contribution_area VARCHAR(100) NOT NULL,
    PRIMARY KEY (member_id, contribution_area),
    CONSTRAINT check_member_other_contribution_area_size CHECK (char_length(contribution_area) BETWEEN 1 AND 100)
);

CREATE TABLE annual_member_information_responses (
    id UUID PRIMARY KEY,
    member_id UUID NOT NULL REFERENCES members(id) ON DELETE RESTRICT,
    survey_cycle INTEGER NOT NULL CHECK (survey_cycle BETWEEN 2000 AND 9999),
    submitted_at TIMESTAMPTZ,
    occupations_details VARCHAR(2000),
    health_condition_status member_information_status_enum NOT NULL,
    health_condition_details VARCHAR(2000),
    religious_vocation_considered member_information_status_enum NOT NULL,
    mass_attendance_frequency member_mass_attendance_frequency_enum NOT NULL,
    saturday_oratorio_impediment_status member_information_status_enum NOT NULL,
    saturday_oratorio_impediment_details VARCHAR(2000),
    formation_and_meeting_interests VARCHAR(2000),
    coordination_interest member_coordination_interest_enum NOT NULL,
    additional_comments VARCHAR(2000),
    oratorio_activity_suggestions VARCHAR(2000),
    instagram_post_suggestions VARCHAR(2000),
    import_batch_id UUID REFERENCES member_information_import_batches(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_annual_member_information_member_cycle UNIQUE (member_id, survey_cycle)
);

CREATE TABLE annual_member_occupations (
    response_id UUID NOT NULL REFERENCES annual_member_information_responses(id) ON DELETE CASCADE,
    occupation member_occupation_enum NOT NULL,
    PRIMARY KEY (response_id, occupation)
);
