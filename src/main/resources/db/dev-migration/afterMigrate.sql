-- noinspection SqlResolveForFile @ routine/"uuidv7"
-- WARNING: THIS LOCAL-DEVELOPMENT CALLBACK CREATES PRIVILEGED ACCOUNTS AND
-- SYNTHETIC SENSITIVE DATA. NEVER ADD THIS LOCATION TO PRODUCTION, STAGING,
-- OPENAPI, MAINTENANCE, DEMONSTRATION, OR INTEGRATION-TEST DEFAULTS.
--
-- The fixed UUIDv7 values and generated UUIDv7 ranges below are the fixture
-- manifest. A manifest identity is never inferred from a mutable display
-- value. See docs/development-fixture.md before changing this callback.

DO $fixture_guard$
DECLARE
    v_execution_marker TEXT := '${gamDevFixtureExecutionEnabled}';
    v_password_hash TEXT := '${gamDevFixturePasswordHash}';
BEGIN
    IF v_execution_marker IS NULL OR btrim(v_execution_marker) <> 'true' THEN
        RAISE EXCEPTION
            'Development fixture execution refused: gamDevFixtureExecutionEnabled must be explicitly true';
    END IF;

    IF v_password_hash IS NULL
        OR v_password_hash !~ '^\{pbkdf2\}[0-9a-fA-F]{96}$'
        OR md5(lower(v_password_hash)) = '6174bd7cf76eb427ab51a1f3b754c3b1' THEN
        RAISE EXCEPTION
            'Development fixture execution refused: a current delegated PBKDF2 hash is required';
    END IF;
END
$fixture_guard$;

DO $development_fixture$
DECLARE
    v_password_hash CONSTANT TEXT := '${gamDevFixturePasswordHash}';
    v_now TIMESTAMPTZ := date_trunc('second', CURRENT_TIMESTAMP);
    v_today DATE := (CURRENT_TIMESTAMP AT TIME ZONE 'America/Sao_Paulo')::date;

    v_role_sudo UUID;
    v_role_coord UUID;
    v_role_oratorio_coord UUID;
    v_role_member UUID;
    v_role_visitor UUID;
    v_role_event_support CONSTANT UUID := '01950000-0002-7000-8000-000000000001';
    v_role_archived_support CONSTANT UUID := '01950000-0002-7000-8000-000000000002';

    v_permission_event_get_member UUID;
    v_permission_event_get_coord UUID;
    v_permission_event_search UUID;
    v_permission_event_get_presences UUID;
    v_permission_gam_location_get UUID;

    v_sudo_account CONSTANT UUID := '01950000-0001-7000-8000-000000000001';
    v_primary_coordinator CONSTANT UUID := '01950000-0001-7000-8000-000000000002';
    v_sacrificial_coordinator CONSTANT UUID := '01950000-0001-7000-8000-000000000003';
    v_oratorio_coordinator CONSTANT UUID := '01950000-0001-7000-8000-000000000004';
    v_active_member_account CONSTANT UUID := '01950000-0001-7000-8000-000000000005';
    v_inactive_member_account CONSTANT UUID := '01950000-0001-7000-8000-000000000006';
    v_direct_registration_account CONSTANT UUID := '01950000-0001-7000-8000-000000000007';
    v_self_submission_account CONSTANT UUID := '01950000-0001-7000-8000-000000000008';
    v_approval_account CONSTANT UUID := '01950000-0001-7000-8000-000000000009';
    v_rejected_history_account CONSTANT UUID := '01950000-0001-7000-8000-000000000013';

    v_member_primary_coordinator CONSTANT UUID := '01950000-0004-7000-8000-000000000001';
    v_member_sacrificial_coordinator CONSTANT UUID := '01950000-0004-7000-8000-000000000002';
    v_member_oratorio_coordinator CONSTANT UUID := '01950000-0004-7000-8000-000000000003';
    v_member_active CONSTANT UUID := '01950000-0004-7000-8000-000000000004';
    v_member_inactive CONSTANT UUID := '01950000-0004-7000-8000-000000000005';

    v_location_sede CONSTANT UUID := '01950000-0005-7000-8000-000000000001';
    v_location_anexo CONSTANT UUID := '01950000-0005-7000-8000-000000000002';
    v_system_oratorio_location UUID;
    v_completion_form_data JSONB;

    v_pdf_base64 CONSTANT TEXT :=
        'JVBERi0xLjQKMSAwIG9iago8PCAvVHlwZSAvQ2F0YWxvZyAvUGFnZXMgMiAwIFIgPj4KZW5kb2JqCjIgMCBvYmoKPDwgL1R5cGUgL1BhZ2VzIC9LaWRzIFszIDAgUl0gL0NvdW50IDEgPj4KZW5kb2JqCjMgMCBvYmoKPDwgL1R5cGUgL1BhZ2UgL1BhcmVudCAyIDAgUiAvTWVkaWFCb3ggWzAgMCA2MTIgNzkyXSAvUmVzb3VyY2VzIDw8IC9Gb250IDw8IC9GMSA1IDAgUiA+PiA+PiAvQ29udGVudHMgNCAwIFIgPj4KZW5kb2JqCjQgMCBvYmoKPDwgL0xlbmd0aCAyMTIgPj4Kc3RyZWFtCkJUCi9GMSAxMCBUZgo1MCA3NTAgVGQKKFNZTlRIRVRJQyBERVZFTE9QTUVOVCBEQVRBKSBUagowIC0xOCBUZAooRm9ybTogRk9STV9VVUlEXzAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwKSBUagowIC0xOCBUZAooU25hcHNob3Q6IFNOQVBTSE9UX1VVSURfMDAwMDAwMDAwMDAwMDAwMDAwMDAwMCkgVGoKMCAtMTggVGQKKFNpZ25lZCBvbjogREFURV9UT0tFTikgVGoKRVQKZW5kc3RyZWFtCmVuZG9iago1IDAgb2JqCjw8IC9UeXBlIC9Gb250IC9TdWJ0eXBlIC9UeXBlMSAvQmFzZUZvbnQgL0hlbHZldGljYSA+PgplbmRvYmoKeHJlZgowIDYKMDAwMDAwMDAwMCA2NTUzNSBmIAowMDAwMDAwMDA5IDAwMDAwIG4gCjAwMDAwMDAwNTggMDAwMDAgbiAKMDAwMDAwMDExNSAwMDAwMCBuIAowMDAwMDAwMjQxIDAwMDAwIG4gCjAwMDAwMDA1MDMgMDAwMDAgbiAKdHJhaWxlcgo8PCAvU2l6ZSA2IC9Sb290IDEgMCBSID4+CnN0YXJ0eHJlZgo1NzMKJSVFT0YK';
BEGIN
    -- Required production-safe reference data is validated before fixture
    -- mutation. The development callback never recreates these records.
    CREATE TEMP TABLE fixture_expected_system_roles (
        code TEXT PRIMARY KEY,
        description TEXT NOT NULL
    ) ON COMMIT DROP;

    INSERT INTO fixture_expected_system_roles
    VALUES
        ('SUDO', 'Developer-controlled unrestricted system access'),
        ('COORD', 'Coordinator access to GAM operational administration'),
        ('ORATORIO_COORD', 'Oratorio operational responsibility for an active Member'),
        ('MEMBER', 'Standard authenticated member access'),
        ('VISITOR',
         'No baseline permission; public visibility is represented by a null event requiredPermissionId');

    CREATE TEMP TABLE fixture_expected_system_permissions (
        code TEXT PRIMARY KEY,
        label TEXT NOT NULL,
        description TEXT NOT NULL
    ) ON COMMIT DROP;

    INSERT INTO fixture_expected_system_permissions
    VALUES
        ('MEMBER_GET', 'View members', 'Allows viewing active members'),
        ('MEMBER_SEARCH', 'Search members', 'Allows searching members'),
        ('MEMBER_ACTIVATION', 'Activate members', 'Allows activating and deactivating members'),
        ('MEMBER_GET_NON_ACTIVE', 'View inactive members', 'Allows viewing non-active members'),
        ('MEMBER_MANAGE', 'Manage members', 'Allows managing members'),
        ('COORDINATOR_MANAGE', 'Manage coordinators',
         'Allows granting and revoking Coordinator designation'),
        ('ACCOUNT_GET', 'View accounts', 'Allows viewing accounts'),
        ('ACCOUNT_SEARCH', 'Search accounts', 'Allows searching accounts'),
        ('ACCOUNT_ROLE_MANAGE', 'Manage account roles', 'Allows adding and removing account roles'),
        ('EVENT_CREATE', 'Create events', 'Allows creating events'),
        ('EVENT_SEARCH', 'Search events', 'Allows searching events'),
        ('EVENT_GET_PRESENCES', 'View event presences', 'Allows viewing presences for an event'),
        ('EVENT_GET_MEMBER', 'View member events', 'Allows viewing events requiring member access'),
        ('EVENT_GET_COORD', 'View coordinator events',
         'Allows viewing events requiring coordinator access'),
        ('EVENT_MANAGE', 'Manage events', 'Allows managing events'),
        ('GAM_LOCATION_GET', 'View GAM locations',
         'Allows directly viewing active GamLocation records'),
        ('GAM_LOCATION_CREATE', 'Create GAM locations', 'Allows creating GamLocation records'),
        ('GAM_LOCATION_MANAGE', 'Manage GAM locations',
         'Allows updating and removing GamLocation records'),
        ('PRESENCES_SEARCH', 'Search presences', 'Allows searching presences'),
        ('PRESENCE_REGISTER', 'Register presences',
         'Allows recording Member attendance at Events'),
        ('PRESENCE_EDIT', 'Edit presences',
         'Allows editing observations on Member attendance records'),
        ('PRESENCE_REMOVE', 'Remove presences',
         'Allows removing mistaken Member attendance records'),
        ('ORATORIO_GET', 'View Oratorios', 'Allows viewing specialized Oratorio details'),
        ('ORATORIO_CREATE', 'Create Oratorios', 'Allows creating Oratorio occurrences'),
        ('ORATORIO_MANAGE', 'Manage Oratorios',
         'Allows managing Oratorio planning and lifecycle'),
        ('ORATORIO_ATTENDANCE_GET', 'View Oratorio attendance',
         'Allows viewing combined Member and Oratoriano attendance trackers'),
        ('ORATORIO_ATTENDANCE_MANAGE', 'Manage Oratorio attendance',
         'Allows recording and correcting Member and Oratoriano attendance'),
        ('ORATORIO_COORD_MANAGE', 'Manage Oratorio coordinators',
         'Allows granting and revoking Oratorio Coordinator designation'),
        ('ORATORIANO_GET', 'View Oratorianos',
         'Allows searching and viewing ordinary Oratoriano profiles'),
        ('ORATORIANO_REGISTER', 'Register Oratorianos', 'Allows registering Oratorianos'),
        ('ORATORIANO_MANAGE', 'Manage Oratorianos',
         'Allows correcting, deleting, and restoring Oratoriano records'),
        ('ORATORIANO_FORM_GET', 'View Oratoriano forms',
         'Allows viewing sensitive Oratoriano form details'),
        ('ORATORIANO_FORM_MANAGE', 'Manage Oratoriano forms',
         'Allows creating and managing Oratoriano form versions'),
        ('ORATORIANO_FORM_PDF_GENERATE', 'Generate Oratoriano form PDFs',
         'Allows creating and rendering identified Oratoriano print snapshots'),
        ('ORATORIANO_FORM_ATTACHMENT_GET', 'Download signed Oratoriano forms',
         'Allows downloading signed Oratoriano form attachments'),
        ('ROLE_GET', 'View roles', 'Allows reading role catalog entries'),
        ('PERMISSION_GET', 'View permissions', 'Allows reading permission catalog entries');

    IF EXISTS (
        SELECT 1
        FROM fixture_expected_system_roles expected
        LEFT JOIN roles actual
          ON actual.name = expected.code
         AND actual.deleted_at IS NULL
        WHERE actual.id IS NULL
           OR NOT actual.system_managed
           OR actual.description IS DISTINCT FROM expected.description
    ) OR EXISTS (
        SELECT 1
        FROM roles actual
        WHERE actual.system_managed
          AND actual.deleted_at IS NULL
          AND NOT EXISTS (
              SELECT 1
              FROM fixture_expected_system_roles expected
              WHERE expected.code = actual.name
          )
    ) THEN
        RAISE EXCEPTION 'Development fixture requires the complete accepted system Role registry';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM fixture_expected_system_permissions expected
        LEFT JOIN permissions actual
          ON actual.code = expected.code
         AND actual.deleted_at IS NULL
        WHERE actual.id IS NULL
           OR NOT actual.system_managed
           OR actual.label IS DISTINCT FROM expected.label
           OR actual.description IS DISTINCT FROM expected.description
    ) OR EXISTS (
        SELECT 1
        FROM permissions actual
        WHERE actual.system_managed
          AND actual.deleted_at IS NULL
          AND NOT EXISTS (
              SELECT 1
              FROM fixture_expected_system_permissions expected
              WHERE expected.code = actual.code
          )
    ) THEN
        RAISE EXCEPTION 'Development fixture requires the complete accepted system Permission registry';
    END IF;

    CREATE TEMP TABLE fixture_expected_system_role_permissions (
        role_code TEXT NOT NULL,
        permission_code TEXT NOT NULL,
        PRIMARY KEY (role_code, permission_code)
    ) ON COMMIT DROP;

    INSERT INTO fixture_expected_system_role_permissions
    SELECT 'SUDO', code
    FROM fixture_expected_system_permissions;

    INSERT INTO fixture_expected_system_role_permissions
    SELECT 'COORD', code
    FROM fixture_expected_system_permissions;

    INSERT INTO fixture_expected_system_role_permissions
    SELECT 'ORATORIO_COORD', code
    FROM fixture_expected_system_permissions
    WHERE code IN (
        'ORATORIO_GET',
        'ORATORIO_CREATE',
        'ORATORIO_MANAGE',
        'ORATORIO_ATTENDANCE_GET',
        'ORATORIO_ATTENDANCE_MANAGE',
        'ORATORIANO_GET',
        'ORATORIANO_REGISTER',
        'ORATORIANO_MANAGE',
        'ORATORIANO_FORM_GET',
        'ORATORIANO_FORM_MANAGE',
        'ORATORIANO_FORM_PDF_GENERATE',
        'ORATORIANO_FORM_ATTACHMENT_GET'
    );

    INSERT INTO fixture_expected_system_role_permissions
    SELECT 'MEMBER', code
    FROM fixture_expected_system_permissions
    WHERE code IN (
        'MEMBER_GET',
        'ACCOUNT_GET',
        'EVENT_SEARCH',
        'EVENT_GET_PRESENCES',
        'EVENT_GET_MEMBER',
        'GAM_LOCATION_GET',
        'ORATORIO_GET'
    );

    IF EXISTS (
        SELECT 1
        FROM fixture_expected_system_role_permissions expected
        JOIN roles role_record
          ON role_record.name = expected.role_code
         AND role_record.deleted_at IS NULL
        JOIN permissions permission_record
          ON permission_record.code = expected.permission_code
         AND permission_record.deleted_at IS NULL
        LEFT JOIN role_permissions relationship
          ON relationship.role_id = role_record.id
         AND relationship.permission_id = permission_record.id
         AND relationship.deleted_at IS NULL
        WHERE relationship.id IS NULL
    ) OR EXISTS (
        SELECT 1
        FROM role_permissions relationship
        JOIN roles role_record
          ON role_record.id = relationship.role_id
         AND role_record.deleted_at IS NULL
        JOIN permissions permission_record
          ON permission_record.id = relationship.permission_id
         AND permission_record.deleted_at IS NULL
        JOIN fixture_expected_system_roles expected_role
          ON expected_role.code = role_record.name
        WHERE relationship.deleted_at IS NULL
          AND NOT EXISTS (
              SELECT 1
              FROM fixture_expected_system_role_permissions expected
              WHERE expected.role_code = role_record.name
                AND expected.permission_code = permission_record.code
          )
    ) THEN
        RAISE EXCEPTION 'Development fixture requires the accepted baseline system Role bundles';
    END IF;

    IF (SELECT count(*) FROM roles
        WHERE name = 'SUDO' AND system_managed AND deleted_at IS NULL) <> 1
        OR (SELECT count(*) FROM roles
        WHERE name = 'COORD' AND system_managed AND deleted_at IS NULL) <> 1
        OR (SELECT count(*) FROM roles
        WHERE name = 'ORATORIO_COORD' AND system_managed AND deleted_at IS NULL) <> 1
        OR (SELECT count(*) FROM roles
        WHERE name = 'MEMBER' AND system_managed AND deleted_at IS NULL) <> 1
        OR (SELECT count(*) FROM roles
        WHERE name = 'VISITOR' AND system_managed AND deleted_at IS NULL) <> 1 THEN
        RAISE EXCEPTION 'Development fixture requires the complete current system Role catalog';
    END IF;

    SELECT id INTO v_role_sudo FROM roles
    WHERE name = 'SUDO' AND system_managed AND deleted_at IS NULL;
    SELECT id INTO v_role_coord FROM roles
    WHERE name = 'COORD' AND system_managed AND deleted_at IS NULL;
    SELECT id INTO v_role_oratorio_coord FROM roles
    WHERE name = 'ORATORIO_COORD' AND system_managed AND deleted_at IS NULL;
    SELECT id INTO v_role_member FROM roles
    WHERE name = 'MEMBER' AND system_managed AND deleted_at IS NULL;
    SELECT id INTO v_role_visitor FROM roles
    WHERE name = 'VISITOR' AND system_managed AND deleted_at IS NULL;

    IF (SELECT count(*) FROM permissions
        WHERE code = 'EVENT_GET_MEMBER' AND system_managed AND deleted_at IS NULL) <> 1
        OR (SELECT count(*) FROM permissions
        WHERE code = 'EVENT_GET_COORD' AND system_managed AND deleted_at IS NULL) <> 1
        OR (SELECT count(*) FROM permissions
        WHERE code = 'EVENT_SEARCH' AND system_managed AND deleted_at IS NULL) <> 1
        OR (SELECT count(*) FROM permissions
        WHERE code = 'EVENT_GET_PRESENCES' AND system_managed AND deleted_at IS NULL) <> 1
        OR (SELECT count(*) FROM permissions
        WHERE code = 'GAM_LOCATION_GET' AND system_managed AND deleted_at IS NULL) <> 1 THEN
        RAISE EXCEPTION 'Development fixture requires its current system Permission prerequisites';
    END IF;

    SELECT id INTO v_permission_event_get_member FROM permissions
    WHERE code = 'EVENT_GET_MEMBER' AND system_managed AND deleted_at IS NULL;
    SELECT id INTO v_permission_event_get_coord FROM permissions
    WHERE code = 'EVENT_GET_COORD' AND system_managed AND deleted_at IS NULL;
    SELECT id INTO v_permission_event_search FROM permissions
    WHERE code = 'EVENT_SEARCH' AND system_managed AND deleted_at IS NULL;
    SELECT id INTO v_permission_event_get_presences FROM permissions
    WHERE code = 'EVENT_GET_PRESENCES' AND system_managed AND deleted_at IS NULL;
    SELECT id INTO v_permission_gam_location_get FROM permissions
    WHERE code = 'GAM_LOCATION_GET' AND system_managed AND deleted_at IS NULL;

    IF (SELECT count(*) FROM gam_locations
        WHERE id = '01950000-0010-7000-8000-000000000001'::uuid
          AND deleted_at IS NULL) <> 1 THEN
        RAISE EXCEPTION
            'Development fixture requires the production-safe Oratorio GamLocation';
    END IF;
    SELECT id INTO v_system_oratorio_location
    FROM gam_locations
    WHERE id = '01950000-0010-7000-8000-000000000001'::uuid
      AND deleted_at IS NULL;

    -- Accounts: twelve named personas, one historical solicitation identity,
    -- and sixty deterministic scale identities.
    CREATE TEMP TABLE fixture_accounts_manifest (
        id UUID PRIMARY KEY,
        email TEXT NOT NULL UNIQUE,
        display_name TEXT NOT NULL
    ) ON COMMIT DROP;

    INSERT INTO fixture_accounts_manifest (id, email, display_name)
    VALUES
        (v_sudo_account, 'dev.sudo@example.com', 'Suporte Técnico Local'),
        (v_primary_coordinator, 'mariana.coord@example.com', 'Mariana Coordenadora'),
        (v_sacrificial_coordinator, 'rafael.coord.sandbox@example.com', 'Rafael Coordenador Sandbox'),
        (v_oratorio_coordinator, 'camila.oratorio@example.com', 'Camila Oratório'),
        (v_active_member_account, 'lucas.member@example.com', 'Lucas Membro'),
        (v_inactive_member_account, 'helena.inactive@example.com', 'Helena Inativa'),
        ('01950000-0001-7000-8000-000000000007', 'beatriz.registration@example.com', 'Beatriz Cadastro'),
        ('01950000-0001-7000-8000-000000000008', 'fernanda.solicitation@example.com', 'Fernanda Solicitação'),
        ('01950000-0001-7000-8000-000000000009', 'joao.approval@example.com', 'João Aprovação'),
        ('01950000-0001-7000-8000-000000000010', 'aline.rejection@example.com', 'Aline Rejeição'),
        ('01950000-0001-7000-8000-000000000011', 'paulo.custom-role@example.com', 'Paulo Apoio'),
        ('01950000-0001-7000-8000-000000000012', 'renata.custom-role@example.com', 'Renata Apoio'),
        (v_rejected_history_account, 'historico.rejeitado@example.com', 'César Histórico');

    INSERT INTO fixture_accounts_manifest (id, email, display_name)
    SELECT
        ('01950000-0001-7100-8000-' || lpad(i::TEXT, 12, '0'))::UUID,
        'membro.' || lpad(i::TEXT, 3, '0') || '@example.com',
        (ARRAY[
            'Ana', 'Breno', 'Célia', 'Davi', 'Érica',
            'Fábio', 'Giovana', 'Heitor', 'Íris', 'José'
        ])[((i - 1) % 10) + 1]
            || ' '
            || (ARRAY[
                'Almeida', 'Barbosa', 'Costa-Silva',
                'D''Ávila', 'Esteves', 'Ferreira'
            ])[((i - 1) / 10) + 1]
    FROM generate_series(1, 60) generated(i);

    IF EXISTS (
        SELECT 1
        FROM accounts account_record
        JOIN fixture_accounts_manifest manifest ON manifest.id = account_record.id
        WHERE account_record.email <> manifest.email
    ) OR EXISTS (
        SELECT 1
        FROM accounts account_record
        JOIN fixture_accounts_manifest manifest ON manifest.email = account_record.email
        WHERE account_record.id <> manifest.id
    ) THEN
        RAISE EXCEPTION 'Development fixture Account manifest collision';
    END IF;

    INSERT INTO accounts AS current_account (
        id, email, password_hash, display_name,
        created_at, created_by, updated_at, updated_by, deleted_at, deleted_by
    )
    SELECT
        manifest.id, manifest.email, v_password_hash, manifest.display_name,
        v_now, NULL, v_now, NULL, NULL, NULL
    FROM fixture_accounts_manifest manifest
    ON CONFLICT (id) DO UPDATE
    SET email = EXCLUDED.email,
        password_hash = EXCLUDED.password_hash,
        display_name = EXCLUDED.display_name,
        updated_at = v_now,
        updated_by = NULL,
        deleted_at = NULL,
        deleted_by = NULL
    WHERE (
        current_account.email,
        current_account.password_hash,
        current_account.display_name,
        current_account.deleted_at,
        current_account.deleted_by
    ) IS DISTINCT FROM (
        EXCLUDED.email,
        EXCLUDED.password_hash,
        EXCLUDED.display_name,
        NULL,
        NULL
    );

    -- Fixture-owned custom Roles and the exact EVENT_SUPPORT bundle.
    IF EXISTS (
        SELECT 1 FROM roles
        WHERE id IN (v_role_event_support, v_role_archived_support)
          AND (id, name) NOT IN (
              (v_role_event_support, 'EVENT_SUPPORT'),
              (v_role_archived_support, 'ARCHIVED_EVENT_SUPPORT')
          )
    ) OR EXISTS (
        SELECT 1 FROM roles
        WHERE name = 'EVENT_SUPPORT' AND id <> v_role_event_support
           OR name = 'ARCHIVED_EVENT_SUPPORT' AND id <> v_role_archived_support
    ) THEN
        RAISE EXCEPTION 'Development fixture custom Role manifest collision';
    END IF;

    INSERT INTO roles AS existing_role (
        id, name, description, system_managed,
        created_at, created_by, updated_at, updated_by, deleted_at, deleted_by
    )
    VALUES (
        v_role_event_support,
        'EVENT_SUPPORT',
        'Read-only Event support for local development',
        FALSE,
        v_now, NULL, v_now, NULL, NULL, NULL
    )
    ON CONFLICT (id) DO UPDATE
    SET name = EXCLUDED.name,
        description = EXCLUDED.description,
        system_managed = FALSE,
        updated_at = v_now,
        updated_by = NULL,
        deleted_at = NULL,
        deleted_by = NULL
    WHERE (
        existing_role.name,
        existing_role.description,
        existing_role.system_managed,
        existing_role.deleted_at,
        existing_role.deleted_by
    ) IS DISTINCT FROM (
        EXCLUDED.name,
        EXCLUDED.description,
        FALSE,
        NULL,
        NULL
    );

    INSERT INTO roles AS archived_role (
        id, name, description, system_managed,
        created_at, created_by, updated_at, updated_by, deleted_at, deleted_by
    )
    VALUES (
        v_role_archived_support,
        'ARCHIVED_EVENT_SUPPORT',
        'Archived read-only Event support Role',
        FALSE,
        v_now, NULL, v_now, NULL, v_now, NULL
    )
    ON CONFLICT (id) DO UPDATE
    SET name = EXCLUDED.name,
        description = EXCLUDED.description,
        system_managed = FALSE,
        updated_at = CASE
            WHEN (
                archived_role.name,
                archived_role.description,
                archived_role.system_managed
            ) IS DISTINCT FROM (
                EXCLUDED.name,
                EXCLUDED.description,
                FALSE
            ) THEN v_now
            ELSE archived_role.updated_at
        END,
        updated_by = CASE
            WHEN (
                archived_role.name,
                archived_role.description,
                archived_role.system_managed
            ) IS DISTINCT FROM (
                EXCLUDED.name,
                EXCLUDED.description,
                FALSE
            ) THEN NULL
            ELSE archived_role.updated_by
        END,
        deleted_at = COALESCE(archived_role.deleted_at, v_now),
        deleted_by = NULL;

    CREATE TEMP TABLE fixture_role_permissions_manifest (
        id UUID PRIMARY KEY,
        permission_id UUID NOT NULL
    ) ON COMMIT DROP;

    INSERT INTO fixture_role_permissions_manifest (id, permission_id)
    VALUES
        ('01950000-0002-7100-8000-000000000001', v_permission_event_search),
        ('01950000-0002-7100-8000-000000000002', v_permission_event_get_member),
        ('01950000-0002-7100-8000-000000000003', v_permission_event_get_presences),
        ('01950000-0002-7100-8000-000000000004', v_permission_gam_location_get);

    IF EXISTS (
        SELECT 1
        FROM role_permissions relationship
        JOIN fixture_role_permissions_manifest manifest ON manifest.id = relationship.id
        WHERE relationship.role_id <> v_role_event_support
           OR relationship.permission_id <> manifest.permission_id
    ) THEN
        RAISE EXCEPTION 'Development fixture Role-Permission manifest collision';
    END IF;

    UPDATE role_permissions relationship
    SET deleted_at = v_now, deleted_by = NULL
    WHERE relationship.role_id = v_role_event_support
      AND relationship.deleted_at IS NULL
      AND NOT EXISTS (
          SELECT 1
          FROM fixture_role_permissions_manifest manifest
          WHERE manifest.id = relationship.id
            AND manifest.permission_id = relationship.permission_id
      );

    INSERT INTO role_permissions AS current_relationship (
        id, role_id, permission_id, created_at, created_by, deleted_at, deleted_by
    )
    SELECT id, v_role_event_support, permission_id, v_now, NULL, NULL, NULL
    FROM fixture_role_permissions_manifest
    ON CONFLICT (id) DO UPDATE
    SET role_id = EXCLUDED.role_id,
        permission_id = EXCLUDED.permission_id,
        deleted_at = NULL,
        deleted_by = NULL
    WHERE (
        current_relationship.role_id,
        current_relationship.permission_id,
        current_relationship.deleted_at,
        current_relationship.deleted_by
    ) IS DISTINCT FROM (
        EXCLUDED.role_id,
        EXCLUDED.permission_id,
        NULL,
        NULL
    );

    -- Member records are reconciled before their lifecycle-owned Role
    -- projection is applied.
    CREATE TEMP TABLE fixture_members_manifest (
        id UUID PRIMARY KEY,
        account_id UUID NOT NULL UNIQUE,
        first_name TEXT NOT NULL,
        surname TEXT NOT NULL,
        birth_date DATE NOT NULL,
        phone_number TEXT NOT NULL,
        status member_status_enum NOT NULL
    ) ON COMMIT DROP;

    INSERT INTO fixture_members_manifest
        (id, account_id, first_name, surname, birth_date, phone_number, status)
    VALUES
        (v_member_primary_coordinator, v_primary_coordinator,
         'Mariana', 'Alves', DATE '1988-04-12', '+5519998111001', 'ACTIVE'),
        (v_member_sacrificial_coordinator, v_sacrificial_coordinator,
         'Rafael', 'Monteiro', DATE '1991-09-23', '+5519998111002', 'ACTIVE'),
        (v_member_oratorio_coordinator, v_oratorio_coordinator,
         'Camila', 'D''Ávila', DATE '1994-02-18', '+5519998111003', 'ACTIVE'),
        (v_member_active, v_active_member_account,
         'Lucas', 'Gonçalves', DATE '1998-11-07', '+5519998111004', 'ACTIVE'),
        (v_member_inactive, v_inactive_member_account,
         'Helena', 'Costa-Silva', DATE '1985-06-30', '+5519998111005', 'INACTIVE');

    INSERT INTO fixture_members_manifest (
        id, account_id, first_name, surname, birth_date, phone_number, status
    )
    SELECT
        ('01950000-0004-7100-8000-' || lpad(i::TEXT, 12, '0'))::UUID,
        ('01950000-0001-7100-8000-' || lpad(i::TEXT, 12, '0'))::UUID,
        (ARRAY[
            'Ana', 'Breno', 'Célia', 'Davi', 'Érica',
            'Fábio', 'Giovana', 'Heitor', 'Íris', 'José'
        ])[((i - 1) % 10) + 1],
        (ARRAY[
            'Almeida', 'Barbosa', 'Costa-Silva',
            'D''Ávila', 'Esteves', 'Ferreira'
        ])[((i - 1) / 10) + 1],
        DATE '1975-01-01' + (i * 113),
        '+55199' || lpad((10000000 + i)::TEXT, 8, '0'),
        CASE WHEN i <= 58 THEN 'ACTIVE' ELSE 'INACTIVE' END::member_status_enum
    FROM generate_series(1, 60) generated(i);

    -- Direct registration consumes this Account's explicitly resettable
    -- lifetime-Member projection. Other non-manifest Members are Developer
    -- data and remain untouched.
    DELETE FROM members
    WHERE account_id = v_direct_registration_account;

    IF EXISTS (
        SELECT 1
        FROM members member_record
        JOIN fixture_members_manifest manifest ON manifest.id = member_record.id
        WHERE member_record.account_id <> manifest.account_id
    ) OR EXISTS (
        SELECT 1
        FROM members member_record
        JOIN fixture_members_manifest manifest ON manifest.account_id = member_record.account_id
        WHERE member_record.id <> manifest.id
    ) THEN
        RAISE EXCEPTION 'Development fixture Member manifest collision';
    END IF;

    INSERT INTO members AS current_member (
        id, account_id, first_name, surname, birth_date, phone_number, status,
        created_at, created_by, updated_at, updated_by, deleted_at, deleted_by
    )
    SELECT
        id, account_id, first_name, surname, birth_date, phone_number, status,
        v_now, NULL, v_now, NULL, NULL, NULL
    FROM fixture_members_manifest
    ON CONFLICT (id) DO UPDATE
    SET account_id = EXCLUDED.account_id,
        first_name = EXCLUDED.first_name,
        surname = EXCLUDED.surname,
        birth_date = EXCLUDED.birth_date,
        phone_number = EXCLUDED.phone_number,
        status = EXCLUDED.status,
        updated_at = v_now,
        updated_by = NULL,
        deleted_at = NULL,
        deleted_by = NULL
    WHERE (
        current_member.account_id,
        current_member.first_name,
        current_member.surname,
        current_member.birth_date,
        current_member.phone_number,
        current_member.status,
        current_member.deleted_at,
        current_member.deleted_by
    ) IS DISTINCT FROM (
        EXCLUDED.account_id,
        EXCLUDED.first_name,
        EXCLUDED.surname,
        EXCLUDED.birth_date,
        EXCLUDED.phone_number,
        EXCLUDED.status,
        NULL,
        NULL
    );

    CREATE TEMP TABLE fixture_account_roles_manifest (
        id UUID PRIMARY KEY,
        account_id UUID NOT NULL,
        role_id UUID NOT NULL,
        UNIQUE (account_id, role_id)
    ) ON COMMIT DROP;

    INSERT INTO fixture_account_roles_manifest (id, account_id, role_id)
    VALUES
        ('01950000-0003-7000-8000-000000000001', v_sudo_account, v_role_sudo),
        ('01950000-0003-7000-8000-000000000002', v_primary_coordinator, v_role_member),
        ('01950000-0003-7000-8000-000000000003', v_primary_coordinator, v_role_coord),
        ('01950000-0003-7000-8000-000000000004', v_sacrificial_coordinator, v_role_member),
        ('01950000-0003-7000-8000-000000000005', v_sacrificial_coordinator, v_role_coord),
        ('01950000-0003-7000-8000-000000000006', v_oratorio_coordinator, v_role_member),
        ('01950000-0003-7000-8000-000000000007', v_oratorio_coordinator, v_role_oratorio_coord),
        ('01950000-0003-7000-8000-000000000008', v_active_member_account, v_role_member),
        ('01950000-0003-7000-8000-000000000009', v_inactive_member_account, v_role_visitor),
        ('01950000-0003-7000-8000-000000000010',
         '01950000-0001-7000-8000-000000000012', v_role_event_support);

    INSERT INTO fixture_account_roles_manifest (id, account_id, role_id)
    SELECT
        ('01950000-0003-7100-8000-' || lpad(i::TEXT, 12, '0'))::UUID,
        ('01950000-0001-7100-8000-' || lpad(i::TEXT, 12, '0'))::UUID,
        CASE WHEN i <= 58 THEN v_role_member ELSE v_role_visitor END
    FROM generate_series(1, 60) generated(i);

    IF EXISTS (
        SELECT 1
        FROM account_roles relationship
        JOIN fixture_account_roles_manifest manifest ON manifest.id = relationship.id
        WHERE relationship.account_id <> manifest.account_id
           OR relationship.role_id <> manifest.role_id
    ) THEN
        RAISE EXCEPTION 'Development fixture Account-Role manifest collision';
    END IF;

    UPDATE account_roles relationship
    SET deleted_at = v_now, deleted_by = NULL
    WHERE relationship.deleted_at IS NULL
      AND relationship.account_id IN (SELECT id FROM fixture_accounts_manifest)
      AND relationship.role_id IN (
          v_role_sudo,
          v_role_member,
          v_role_coord,
          v_role_visitor,
          v_role_oratorio_coord,
          v_role_event_support
      )
      AND NOT EXISTS (
          SELECT 1
          FROM fixture_account_roles_manifest manifest
          WHERE manifest.id = relationship.id
            AND manifest.account_id = relationship.account_id
            AND manifest.role_id = relationship.role_id
      );

    INSERT INTO account_roles AS current_relationship (
        id, account_id, role_id, created_at, created_by, deleted_at, deleted_by
    )
    SELECT id, account_id, role_id, v_now, NULL, NULL, NULL
    FROM fixture_account_roles_manifest
    ON CONFLICT (id) DO UPDATE
    SET account_id = EXCLUDED.account_id,
        role_id = EXCLUDED.role_id,
        deleted_at = NULL,
        deleted_by = NULL
    WHERE (
        current_relationship.account_id,
        current_relationship.role_id,
        current_relationship.deleted_at,
        current_relationship.deleted_by
    ) IS DISTINCT FROM (
        EXCLUDED.account_id,
        EXCLUDED.role_id,
        NULL,
        NULL
    );

    -- Ordinary development GamLocations supplement, but never redefine, the
    -- production-safe Oratorio location.
    CREATE TEMP TABLE fixture_locations_manifest (
        id UUID PRIMARY KEY,
        name TEXT NOT NULL,
        street TEXT,
        city TEXT NOT NULL,
        state TEXT NOT NULL,
        postal_code TEXT,
        country_code TEXT NOT NULL,
        latitude NUMERIC,
        longitude NUMERIC,
        identity_name TEXT NOT NULL,
        identity_street TEXT NOT NULL,
        identity_city TEXT NOT NULL,
        identity_state TEXT NOT NULL,
        identity_postal_code TEXT NOT NULL,
        identity_country_code TEXT NOT NULL,
        deleted BOOLEAN NOT NULL
    ) ON COMMIT DROP;

    INSERT INTO fixture_locations_manifest
    VALUES
        (v_location_sede, 'Sede Principal GAM', 'Rua Fictícia, 123',
         'São Paulo', 'SP', '01000-000', 'BR', -23.550520, -46.633300,
         'sede principal gam', 'rua ficticia, 123', 'sao paulo', 'sp',
         '01000-000', 'br', FALSE),
        (v_location_anexo, 'Salão de Eventos Anexo', 'Avenida Brasil, 456',
         'Rio de Janeiro', 'RJ', '20000-000', 'BR', -22.906847, -43.172896,
         'salao de eventos anexo', 'avenida brasil, 456', 'rio de janeiro', 'rj',
         '20000-000', 'br', FALSE),
        ('01950000-0005-7000-8000-000000000003',
         'Casa de Encontros Esperança', 'Rua das Acácias, 87',
         'Piracicaba', 'SP', '13400-101', 'BR', NULL, NULL,
         'casa de encontros esperanca', 'rua das acacias, 87', 'piracicaba', 'sp',
         '13400-101', 'br', FALSE),
        ('01950000-0005-7000-8000-000000000004',
         'Quadra Comunitária Horizonte', 'Rua do Horizonte, 40',
         'Piracicaba', 'SP', '13401-202', 'BR', NULL, NULL,
         'quadra comunitaria horizonte', 'rua do horizonte, 40', 'piracicaba', 'sp',
         '13401-202', 'br', FALSE),
        ('01950000-0005-7000-8000-000000000005',
         'Espaço Comunitário Arquivado', 'Rua da Memória, 12',
         'Piracicaba', 'SP', '13402-303', 'BR', NULL, NULL,
         'espaco comunitario arquivado', 'rua da memoria, 12', 'piracicaba', 'sp',
         '13402-303', 'br', TRUE);

    -- The manifest UUID establishes ownership. Mutable metadata and identity
    -- fields are reconciled below; only an accepted identity owned by another
    -- UUID is a collision.
    IF EXISTS (
        SELECT 1
        FROM gam_locations location_record
        JOIN fixture_locations_manifest manifest
          ON (
              location_record.identity_name,
              location_record.identity_street,
              location_record.identity_city,
              location_record.identity_state,
              location_record.identity_postal_code,
              location_record.identity_country_code
          ) = (
              manifest.identity_name,
              manifest.identity_street,
              manifest.identity_city,
              manifest.identity_state,
              manifest.identity_postal_code,
              manifest.identity_country_code
          )
        WHERE location_record.id <> manifest.id
    ) THEN
        RAISE EXCEPTION 'Development fixture GamLocation manifest collision';
    END IF;

    INSERT INTO gam_locations AS current_location (
        id, name, street, city, state, postal_code, country_code, latitude, longitude,
        identity_name, identity_street, identity_city, identity_state,
        identity_postal_code, identity_country_code,
        created_at, created_by, updated_at, updated_by, deleted_at, deleted_by
    )
    SELECT
        id, name, street, city, state, postal_code, country_code, latitude, longitude,
        identity_name, identity_street, identity_city, identity_state,
        identity_postal_code, identity_country_code,
        v_now, NULL, v_now, NULL,
        CASE WHEN deleted THEN v_now ELSE NULL END, NULL
    FROM fixture_locations_manifest
    ON CONFLICT (id) DO UPDATE
    SET name = EXCLUDED.name,
        street = EXCLUDED.street,
        city = EXCLUDED.city,
        state = EXCLUDED.state,
        postal_code = EXCLUDED.postal_code,
        country_code = EXCLUDED.country_code,
        latitude = EXCLUDED.latitude,
        longitude = EXCLUDED.longitude,
        identity_name = EXCLUDED.identity_name,
        identity_street = EXCLUDED.identity_street,
        identity_city = EXCLUDED.identity_city,
        identity_state = EXCLUDED.identity_state,
        identity_postal_code = EXCLUDED.identity_postal_code,
        identity_country_code = EXCLUDED.identity_country_code,
        updated_at = CASE
            WHEN (
                current_location.name,
                current_location.street,
                current_location.city,
                current_location.state,
                current_location.postal_code,
                current_location.country_code,
                current_location.latitude,
                current_location.longitude,
                current_location.identity_name,
                current_location.identity_street,
                current_location.identity_city,
                current_location.identity_state,
                current_location.identity_postal_code,
                current_location.identity_country_code
            ) IS DISTINCT FROM (
                EXCLUDED.name,
                EXCLUDED.street,
                EXCLUDED.city,
                EXCLUDED.state,
                EXCLUDED.postal_code,
                EXCLUDED.country_code,
                EXCLUDED.latitude,
                EXCLUDED.longitude,
                EXCLUDED.identity_name,
                EXCLUDED.identity_street,
                EXCLUDED.identity_city,
                EXCLUDED.identity_state,
                EXCLUDED.identity_postal_code,
                EXCLUDED.identity_country_code
            )
            OR (
                EXCLUDED.deleted_at IS NULL
                AND current_location.deleted_at IS NOT NULL
            ) THEN v_now
            ELSE current_location.updated_at
        END,
        updated_by = CASE
            WHEN (
                current_location.name,
                current_location.street,
                current_location.city,
                current_location.state,
                current_location.postal_code,
                current_location.country_code,
                current_location.latitude,
                current_location.longitude,
                current_location.identity_name,
                current_location.identity_street,
                current_location.identity_city,
                current_location.identity_state,
                current_location.identity_postal_code,
                current_location.identity_country_code
            ) IS DISTINCT FROM (
                EXCLUDED.name,
                EXCLUDED.street,
                EXCLUDED.city,
                EXCLUDED.state,
                EXCLUDED.postal_code,
                EXCLUDED.country_code,
                EXCLUDED.latitude,
                EXCLUDED.longitude,
                EXCLUDED.identity_name,
                EXCLUDED.identity_street,
                EXCLUDED.identity_city,
                EXCLUDED.identity_state,
                EXCLUDED.identity_postal_code,
                EXCLUDED.identity_country_code
            )
            OR (
                EXCLUDED.deleted_at IS NULL
                AND current_location.deleted_at IS NOT NULL
            ) THEN NULL
            ELSE current_location.updated_by
        END,
        deleted_at = CASE
            WHEN EXCLUDED.deleted_at IS NULL THEN NULL
            ELSE COALESCE(current_location.deleted_at, v_now)
        END,
        deleted_by = NULL
    WHERE (
        current_location.name,
        current_location.street,
        current_location.city,
        current_location.state,
        current_location.postal_code,
        current_location.country_code,
        current_location.latitude,
        current_location.longitude,
        current_location.identity_name,
        current_location.identity_street,
        current_location.identity_city,
        current_location.identity_state,
        current_location.identity_postal_code,
        current_location.identity_country_code,
        current_location.deleted_at IS NOT NULL
    ) IS DISTINCT FROM (
        EXCLUDED.name,
        EXCLUDED.street,
        EXCLUDED.city,
        EXCLUDED.state,
        EXCLUDED.postal_code,
        EXCLUDED.country_code,
        EXCLUDED.latitude,
        EXCLUDED.longitude,
        EXCLUDED.identity_name,
        EXCLUDED.identity_street,
        EXCLUDED.identity_city,
        EXCLUDED.identity_state,
        EXCLUDED.identity_postal_code,
        EXCLUDED.identity_country_code,
        EXCLUDED.deleted_at IS NOT NULL
    );

    -- Generic and specialized Event state catalog. All relative instants are
    -- recalculated from the São Paulo local date.
    CREATE TEMP TABLE fixture_events_manifest (
        id UUID PRIMARY KEY,
        title TEXT NOT NULL,
        description TEXT NOT NULL,
        gam_location_id UUID NOT NULL,
        required_permission_id UUID,
        type event_type_enum NOT NULL,
        status event_status_enum NOT NULL,
        cancellation_reason TEXT,
        begin_date TIMESTAMPTZ NOT NULL,
        end_date TIMESTAMPTZ NOT NULL
    ) ON COMMIT DROP;

    INSERT INTO fixture_events_manifest
    VALUES
        ('01950000-0006-7000-8000-000000000001',
         'Reunião de Coordenadores', 'Planejamento estratégico do mês.',
         v_location_sede, v_permission_event_get_coord, 'GENERIC', 'SCHEDULED', NULL,
         (v_today + 14 + TIME '19:00') AT TIME ZONE 'America/Sao_Paulo',
         (v_today + 14 + TIME '21:00') AT TIME ZONE 'America/Sao_Paulo'),
        ('01950000-0006-7000-8000-000000000002',
         'Encontro Semanal GAM', 'Encontro geral com acolhida.',
         v_location_anexo, NULL, 'GENERIC', 'SCHEDULED', NULL,
         (v_today + 10 + TIME '18:00') AT TIME ZONE 'America/Sao_Paulo',
         (v_today + 10 + TIME '21:00') AT TIME ZONE 'America/Sao_Paulo'),
        ('01950000-0006-7000-8000-000000000003',
         'Palestra sobre Voluntariado (Passado)', 'Atividade histórica concluída.',
         v_location_sede, NULL, 'GENERIC', 'LOCKED', NULL,
         (v_today - 20 + TIME '18:00') AT TIME ZONE 'America/Sao_Paulo',
         (v_today - 20 + TIME '20:00') AT TIME ZONE 'America/Sao_Paulo'),
        ('01950000-0006-7000-8000-000000000004',
         'Evento Portas Abertas', 'Atividade pública de apresentação.',
         v_location_sede, NULL, 'GENERIC', 'SCHEDULED', NULL,
         (v_today + 21 + TIME '15:00') AT TIME ZONE 'America/Sao_Paulo',
         (v_today + 21 + TIME '18:00') AT TIME ZONE 'America/Sao_Paulo'),
        ('01950000-0006-7000-8000-000000000005',
         'Oficina para Membros', 'Atividade restrita a Membros.',
         v_location_anexo, v_permission_event_get_member, 'GENERIC', 'SCHEDULED', NULL,
         (v_today + 28 + TIME '14:00') AT TIME ZONE 'America/Sao_Paulo',
         (v_today + 28 + TIME '17:00') AT TIME ZONE 'America/Sao_Paulo'),
        ('01950000-0006-7000-8000-000000000006',
         'Evento concluído para bloqueio', 'Alvo sacrificial de bloqueio.',
         v_location_sede, v_permission_event_get_member, 'GENERIC', 'COMPLETED', NULL,
         (v_today - 10 + TIME '14:00') AT TIME ZONE 'America/Sao_Paulo',
         (v_today - 10 + TIME '17:00') AT TIME ZONE 'America/Sao_Paulo'),
        ('01950000-0006-7000-8000-000000000007',
         'Evento concluído para finalização', 'Alvo sacrificial de finalização.',
         v_location_sede, NULL, 'GENERIC', 'COMPLETED', NULL,
         (v_today - 12 + TIME '14:00') AT TIME ZONE 'America/Sao_Paulo',
         (v_today - 12 + TIME '17:00') AT TIME ZONE 'America/Sao_Paulo'),
        ('01950000-0006-7000-8000-000000000008',
         'Evento bloqueado para reabertura', 'Alvo sacrificial bloqueado.',
         v_location_anexo, NULL, 'GENERIC', 'LOCKED', NULL,
         (v_today - 14 + TIME '14:00') AT TIME ZONE 'America/Sao_Paulo',
         (v_today - 14 + TIME '17:00') AT TIME ZONE 'America/Sao_Paulo'),
        ('01950000-0006-7000-8000-000000000009',
         'Evento finalizado para reabertura', 'Alvo sacrificial finalizado.',
         v_location_anexo, NULL, 'GENERIC', 'FINALIZED', NULL,
         (v_today - 16 + TIME '14:00') AT TIME ZONE 'America/Sao_Paulo',
         (v_today - 16 + TIME '17:00') AT TIME ZONE 'America/Sao_Paulo'),
        ('01950000-0006-7000-8000-000000000010',
         'Evento agendado para cancelamento', 'Alvo sacrificial de cancelamento.',
         v_location_sede, NULL, 'GENERIC', 'SCHEDULED', NULL,
         (v_today + 35 + TIME '14:00') AT TIME ZONE 'America/Sao_Paulo',
         (v_today + 35 + TIME '17:00') AT TIME ZONE 'America/Sao_Paulo'),
        ('01950000-0006-7000-8000-000000000011',
         'Evento removível sem presenças', 'Alvo sacrificial de remoção.',
         v_location_anexo, NULL, 'GENERIC', 'SCHEDULED', NULL,
         (v_today + 42 + TIME '14:00') AT TIME ZONE 'America/Sao_Paulo',
         (v_today + 42 + TIME '17:00') AT TIME ZONE 'America/Sao_Paulo'),
        ('01950000-0006-7000-8000-000000000012',
         'Evento bloqueado por presença', 'Possui uma Presença ativa.',
         v_location_sede, NULL, 'GENERIC', 'COMPLETED', NULL,
         (v_today - 18 + TIME '14:00') AT TIME ZONE 'America/Sao_Paulo',
         (v_today - 18 + TIME '17:00') AT TIME ZONE 'America/Sao_Paulo'),
        ('01950000-0006-7000-8000-000000000013',
         'Evento com presença removida', 'Possui somente histórico removido.',
         v_location_sede, NULL, 'GENERIC', 'COMPLETED', NULL,
         (v_today - 22 + TIME '14:00') AT TIME ZONE 'America/Sao_Paulo',
         (v_today - 22 + TIME '17:00') AT TIME ZONE 'America/Sao_Paulo'),
        ('01950000-0006-7000-8000-000000000014',
         'Evento cancelado', 'Exemplo explícito de cancelamento.',
         v_location_anexo, NULL, 'GENERIC', 'CANCELLED', 'Cancelado para exercício local.',
         (v_today - 24 + TIME '14:00') AT TIME ZONE 'America/Sao_Paulo',
         (v_today - 24 + TIME '17:00') AT TIME ZONE 'America/Sao_Paulo'),
        ('01950000-0006-7100-8000-000000000001',
         'Oratório', '', v_system_oratorio_location, v_permission_event_get_member,
         'ORATORIO', 'SCHEDULED', NULL,
         (v_today + 7 + TIME '14:00') AT TIME ZONE 'America/Sao_Paulo',
         (v_today + 7 + TIME '17:00') AT TIME ZONE 'America/Sao_Paulo'),
        ('01950000-0006-7100-8000-000000000002',
         'Oratório', '', v_system_oratorio_location, v_permission_event_get_member,
         'ORATORIO', 'LOCKED', NULL,
         (v_today - 8 + TIME '14:00') AT TIME ZONE 'America/Sao_Paulo',
         (v_today - 8 + TIME '17:00') AT TIME ZONE 'America/Sao_Paulo'),
        ('01950000-0006-7100-8000-000000000003',
         'Oratório', '', v_system_oratorio_location, v_permission_event_get_member,
         'ORATORIO', 'LOCKED', NULL,
         (v_today - 10 + TIME '14:00') AT TIME ZONE 'America/Sao_Paulo',
         (v_today - 10 + TIME '17:00') AT TIME ZONE 'America/Sao_Paulo'),
        ('01950000-0006-7100-8000-000000000004',
         'Oratório', '', v_system_oratorio_location, v_permission_event_get_member,
         'ORATORIO', 'FINALIZED', NULL,
         (v_today - 12 + TIME '14:00') AT TIME ZONE 'America/Sao_Paulo',
         (v_today - 12 + TIME '17:00') AT TIME ZONE 'America/Sao_Paulo'),
        ('01950000-0006-7100-8000-000000000005',
         'Oratório', '', v_system_oratorio_location, v_permission_event_get_member,
         'ORATORIO', 'CANCELLED', 'Cancelado para exercício local.',
         (v_today - 14 + TIME '14:00') AT TIME ZONE 'America/Sao_Paulo',
         (v_today - 14 + TIME '17:00') AT TIME ZONE 'America/Sao_Paulo'),
        ('01950000-0006-7100-8000-000000000006',
         'Oratório', '', v_system_oratorio_location, v_permission_event_get_member,
         'ORATORIO', 'COMPLETED', NULL,
         (v_today - 35 + TIME '14:00') AT TIME ZONE 'America/Sao_Paulo',
         (v_today - 35 + TIME '17:00') AT TIME ZONE 'America/Sao_Paulo'),
        ('01950000-0006-7100-8000-000000000007',
         'Oratório', '', v_system_oratorio_location, v_permission_event_get_member,
         'ORATORIO', 'COMPLETED', NULL,
         (v_today - 370 + TIME '14:00') AT TIME ZONE 'America/Sao_Paulo',
         (v_today - 370 + TIME '17:00') AT TIME ZONE 'America/Sao_Paulo'),
        ('01950000-0006-7100-8000-000000000008',
         'Oratório', '', v_system_oratorio_location, v_permission_event_get_member,
         'ORATORIO', 'SCHEDULED', NULL,
         (v_today + 30 + TIME '14:00') AT TIME ZONE 'America/Sao_Paulo',
         (v_today + 30 + TIME '17:00') AT TIME ZONE 'America/Sao_Paulo'),
        ('01950000-0006-7100-8000-000000000009',
         'Oratório', '', v_system_oratorio_location, v_permission_event_get_member,
         'ORATORIO', 'COMPLETED', NULL,
         (v_today - 20 + TIME '14:00') AT TIME ZONE 'America/Sao_Paulo',
         (v_today - 20 + TIME '17:00') AT TIME ZONE 'America/Sao_Paulo');

    IF EXISTS (
        SELECT 1
        FROM events event_record
        JOIN fixture_events_manifest manifest ON manifest.id = event_record.id
        WHERE event_record.type <> manifest.type
    ) THEN
        RAISE EXCEPTION 'Development fixture Event manifest collision';
    END IF;

    INSERT INTO events AS current_event (
        id, title, description, gam_location_id, required_permission_id,
        type, status, cancellation_reason, begin_date, end_date,
        created_at, created_by, updated_at, updated_by, deleted_at, deleted_by
    )
    SELECT
        id, title, description, gam_location_id, required_permission_id,
        type, status, cancellation_reason, begin_date, end_date,
        v_now, NULL, v_now, NULL, NULL, NULL
    FROM fixture_events_manifest
    ON CONFLICT (id) DO UPDATE
    SET title = EXCLUDED.title,
        description = EXCLUDED.description,
        gam_location_id = EXCLUDED.gam_location_id,
        required_permission_id = EXCLUDED.required_permission_id,
        type = EXCLUDED.type,
        status = EXCLUDED.status,
        cancellation_reason = EXCLUDED.cancellation_reason,
        begin_date = EXCLUDED.begin_date,
        end_date = EXCLUDED.end_date,
        updated_at = v_now,
        updated_by = NULL,
        deleted_at = NULL,
        deleted_by = NULL
    WHERE (
        current_event.title,
        current_event.description,
        current_event.gam_location_id,
        current_event.required_permission_id,
        current_event.type,
        current_event.status,
        current_event.cancellation_reason,
        current_event.begin_date,
        current_event.end_date,
        current_event.deleted_at,
        current_event.deleted_by
    ) IS DISTINCT FROM (
        EXCLUDED.title,
        EXCLUDED.description,
        EXCLUDED.gam_location_id,
        EXCLUDED.required_permission_id,
        EXCLUDED.type,
        EXCLUDED.status,
        EXCLUDED.cancellation_reason,
        EXCLUDED.begin_date,
        EXCLUDED.end_date,
        NULL,
        NULL
    );

    CREATE TEMP TABLE fixture_oratorios_manifest (
        id UUID PRIMARY KEY,
        local_date DATE NOT NULL UNIQUE,
        lanche_description TEXT,
        gincana_description TEXT,
        boa_tarde_criancas_plan TEXT,
        boa_tarde_jovens_plan TEXT
    ) ON COMMIT DROP;

    INSERT INTO fixture_oratorios_manifest
    VALUES
        ('01950000-0006-7100-8000-000000000001', v_today + 7,
         'Frutas e suco.', 'Circuito cooperativo.', 'Amizade e serviço.', 'Esperança em ação.'),
        ('01950000-0006-7100-8000-000000000002', v_today - 8,
         NULL, 'Gincana das cores.', NULL, 'Conversa em pequenos grupos.'),
        ('01950000-0006-7100-8000-000000000003', v_today - 10,
         'Lanche compartilhado.', NULL, 'Cuidado com a casa comum.', NULL),
        ('01950000-0006-7100-8000-000000000004', v_today - 12,
         NULL, NULL, NULL, NULL),
        ('01950000-0006-7100-8000-000000000005', v_today - 14,
         'Planejamento não realizado.', NULL, NULL, NULL),
        ('01950000-0006-7100-8000-000000000006', v_today - 35,
         'Bolo simples e frutas.', 'Caça ao tesouro solidária.', NULL, NULL),
        ('01950000-0006-7100-8000-000000000007', v_today - 370,
         'Lanche histórico sintético.', NULL, NULL, 'Memória e gratidão.'),
        ('01950000-0006-7100-8000-000000000008', v_today + 30,
         NULL, NULL, NULL, NULL),
        ('01950000-0006-7100-8000-000000000009', v_today - 20,
         NULL, 'Registro com presença removida.', NULL, NULL);

    IF EXISTS (
        SELECT 1
        FROM oratorios occurrence
        JOIN fixture_oratorios_manifest manifest ON manifest.id = occurrence.id
        WHERE occurrence.event_id <> manifest.id
    ) OR EXISTS (
        SELECT 1
        FROM oratorios occurrence
        JOIN fixture_oratorios_manifest manifest ON manifest.local_date = occurrence.local_date
        WHERE occurrence.id <> manifest.id
          AND occurrence.deleted_at IS NULL
    ) THEN
        RAISE EXCEPTION 'Development fixture Oratorio manifest collision';
    END IF;

    INSERT INTO oratorios AS current_occurrence (
        id, event_id, cancellation_reason, local_date,
        lanche_description, gincana_description,
        boa_tarde_criancas_plan, boa_tarde_jovens_plan,
        created_at, created_by, updated_at, updated_by, deleted_at, deleted_by
    )
    SELECT
        id, id, NULL, local_date,
        lanche_description, gincana_description,
        boa_tarde_criancas_plan, boa_tarde_jovens_plan,
        v_now, NULL, v_now, NULL, NULL, NULL
    FROM fixture_oratorios_manifest
    ON CONFLICT (id) DO UPDATE
    SET event_id = EXCLUDED.event_id,
        cancellation_reason = NULL,
        local_date = EXCLUDED.local_date,
        lanche_description = EXCLUDED.lanche_description,
        gincana_description = EXCLUDED.gincana_description,
        boa_tarde_criancas_plan = EXCLUDED.boa_tarde_criancas_plan,
        boa_tarde_jovens_plan = EXCLUDED.boa_tarde_jovens_plan,
        updated_at = v_now,
        updated_by = NULL,
        deleted_at = NULL,
        deleted_by = NULL
    WHERE (
        current_occurrence.event_id,
        current_occurrence.cancellation_reason,
        current_occurrence.local_date,
        current_occurrence.lanche_description,
        current_occurrence.gincana_description,
        current_occurrence.boa_tarde_criancas_plan,
        current_occurrence.boa_tarde_jovens_plan,
        current_occurrence.deleted_at,
        current_occurrence.deleted_by
    ) IS DISTINCT FROM (
        EXCLUDED.event_id,
        NULL,
        EXCLUDED.local_date,
        EXCLUDED.lanche_description,
        EXCLUDED.gincana_description,
        EXCLUDED.boa_tarde_criancas_plan,
        EXCLUDED.boa_tarde_jovens_plan,
        NULL,
        NULL
    );

    CREATE TEMP TABLE fixture_teams_manifest (
        oratorio_id UUID NOT NULL,
        member_id UUID NOT NULL,
        team_type oratorio_team_type_enum NOT NULL,
        PRIMARY KEY (oratorio_id, member_id, team_type)
    ) ON COMMIT DROP;

    INSERT INTO fixture_teams_manifest
    VALUES
        ('01950000-0006-7100-8000-000000000001',
         '01950000-0004-7100-8000-000000000001', 'LANCHE'),
        ('01950000-0006-7100-8000-000000000001',
         '01950000-0004-7100-8000-000000000002', 'GINCANA'),
        ('01950000-0006-7100-8000-000000000001',
         '01950000-0004-7100-8000-000000000003', 'BOA_TARDE_CRIANCAS'),
        ('01950000-0006-7100-8000-000000000001',
         '01950000-0004-7100-8000-000000000004', 'BOA_TARDE_JOVENS');

    -- Team ownership is the exact manifest triple. Additional relationships on
    -- canonical Oratorios remain Developer-created data and are preserved.
    INSERT INTO oratorio_team_assignments (
        oratorio_id, member_id, team_type, created_at, created_by
    )
    SELECT oratorio_id, member_id, team_type, v_now, NULL
    FROM fixture_teams_manifest
    ON CONFLICT DO NOTHING;

    -- Membership-solicitation workflow catalog.
    CREATE TEMP TABLE fixture_solicitations_manifest (
        id UUID PRIMARY KEY,
        account_id UUID NOT NULL,
        first_name TEXT NOT NULL,
        surname TEXT NOT NULL,
        birth_date DATE NOT NULL,
        phone_number TEXT NOT NULL,
        justification TEXT NOT NULL,
        status membership_solicitation_status_enum NOT NULL,
        reviewed_by_account_id UUID,
        decided_at TIMESTAMPTZ,
        review_reason TEXT,
        member_id UUID
    ) ON COMMIT DROP;

    INSERT INTO fixture_solicitations_manifest
    VALUES
        ('01950000-0009-7000-8000-000000000001',
         '01950000-0001-7000-8000-000000000009',
         'João', 'Pereira', DATE '1999-03-15', '+5519998222001',
         'Deseja colaborar nas atividades semanais.', 'PENDING',
         NULL, NULL, NULL, NULL),
        ('01950000-0009-7000-8000-000000000002',
         '01950000-0001-7000-8000-000000000010',
         'Aline', 'Moraes', DATE '1996-08-10', '+5519998222002',
         'Conheceu o GAM em uma atividade comunitária.', 'PENDING',
         NULL, NULL, NULL, NULL),
        ('01950000-0009-7000-8000-000000000003',
         '01950000-0001-7100-8000-000000000001',
         'Ana', 'Almeida', DATE '1975-04-24', '+5519910000001',
         'Solicitação histórica aprovada.', 'APPROVED',
         v_primary_coordinator,
         (v_today - 90 + TIME '12:00') AT TIME ZONE 'America/Sao_Paulo',
         'Aprovada para compor as atividades.',
         '01950000-0004-7100-8000-000000000001'),
        ('01950000-0009-7000-8000-000000000004',
         v_rejected_history_account,
         'César', 'Oliveira', DATE '1989-05-21', '+5519998222004',
         'Solicitação histórica para consulta.', 'REJECTED',
         v_primary_coordinator,
         (v_today - 60 + TIME '12:00') AT TIME ZONE 'America/Sao_Paulo',
         'Rejeitada após conversa de disponibilidade.', NULL);

    -- Self-submission consumes Fernanda's explicitly resettable active
    -- solicitation projection. Historical or unrelated Account
    -- solicitations are not part of this projection.
    UPDATE membership_solicitations
    SET deleted_at = v_now,
        deleted_by = NULL
    WHERE account_id = v_self_submission_account
      AND deleted_at IS NULL;

    IF EXISTS (
        SELECT 1
        FROM membership_solicitations solicitation
        JOIN fixture_solicitations_manifest manifest ON manifest.id = solicitation.id
        WHERE solicitation.account_id <> manifest.account_id
    ) THEN
        RAISE EXCEPTION 'Development fixture Membership Solicitation manifest collision';
    END IF;

    INSERT INTO membership_solicitations AS current_solicitation (
        id, account_id, first_name, surname, birth_date, phone_number,
        justification, status, reviewed_by_account_id, decided_at,
        review_reason, member_id, version,
        created_at, created_by, updated_at, updated_by, deleted_at, deleted_by
    )
    SELECT
        id, account_id, first_name, surname, birth_date, phone_number,
        justification, status, reviewed_by_account_id, decided_at,
        review_reason, member_id, 0,
        v_now, NULL, v_now, NULL, NULL, NULL
    FROM fixture_solicitations_manifest
    ON CONFLICT (id) DO UPDATE
    SET account_id = EXCLUDED.account_id,
        first_name = EXCLUDED.first_name,
        surname = EXCLUDED.surname,
        birth_date = EXCLUDED.birth_date,
        phone_number = EXCLUDED.phone_number,
        justification = EXCLUDED.justification,
        status = EXCLUDED.status,
        reviewed_by_account_id = EXCLUDED.reviewed_by_account_id,
        decided_at = EXCLUDED.decided_at,
        review_reason = EXCLUDED.review_reason,
        member_id = EXCLUDED.member_id,
        version = 0,
        updated_at = v_now,
        updated_by = NULL,
        deleted_at = NULL,
        deleted_by = NULL
    WHERE (
        current_solicitation.account_id,
        current_solicitation.first_name,
        current_solicitation.surname,
        current_solicitation.birth_date,
        current_solicitation.phone_number,
        current_solicitation.justification,
        current_solicitation.status,
        current_solicitation.reviewed_by_account_id,
        current_solicitation.decided_at,
        current_solicitation.review_reason,
        current_solicitation.member_id,
        current_solicitation.version,
        current_solicitation.deleted_at,
        current_solicitation.deleted_by
    ) IS DISTINCT FROM (
        EXCLUDED.account_id,
        EXCLUDED.first_name,
        EXCLUDED.surname,
        EXCLUDED.birth_date,
        EXCLUDED.phone_number,
        EXCLUDED.justification,
        EXCLUDED.status,
        EXCLUDED.reviewed_by_account_id,
        EXCLUDED.decided_at,
        EXCLUDED.review_reason,
        EXCLUDED.member_id,
        0,
        NULL,
        NULL
    );

    -- Approval consumes João's explicitly resettable lifetime-Member
    -- projection. The canonical solicitation above is detached first so the
    -- endpoint-created Member identity can be removed safely.
    DELETE FROM members
    WHERE account_id = v_approval_account;

    -- Common Member Presences include varied observations, an inactive Member,
    -- active blockers, and removed relationship history.
    CREATE TEMP TABLE fixture_presences_manifest (
        id UUID PRIMARY KEY,
        member_id UUID NOT NULL,
        event_id UUID NOT NULL,
        observations TEXT,
        deleted BOOLEAN NOT NULL,
        UNIQUE (member_id, event_id)
    ) ON COMMIT DROP;

    INSERT INTO fixture_presences_manifest
    VALUES
        ('01950000-000a-7000-8000-000000000001',
         v_member_primary_coordinator, '01950000-0006-7000-8000-000000000001',
         'Conduzirá a pauta de acolhida.', FALSE),
        ('01950000-000a-7000-8000-000000000002',
         v_member_active, '01950000-0006-7000-8000-000000000003',
         NULL, FALSE),
        ('01950000-000a-7000-8000-000000000003',
         v_member_sacrificial_coordinator, '01950000-0006-7000-8000-000000000012',
         'Presença ativa usada no cenário de conflito.', FALSE),
        ('01950000-000a-7000-8000-000000000004',
         v_member_active, '01950000-0006-7000-8000-000000000013',
         NULL, TRUE),
        ('01950000-000a-7000-8000-000000000005',
         v_member_primary_coordinator, '01950000-0006-7100-8000-000000000006',
         NULL, FALSE),
        ('01950000-000a-7000-8000-000000000006',
         v_member_inactive, '01950000-0006-7100-8000-000000000006',
         'Histórico preservado de Membro inativo.', FALSE),
        ('01950000-000a-7000-8000-000000000007',
         v_member_active, '01950000-0006-7100-8000-000000000007',
         NULL, FALSE);

    INSERT INTO fixture_presences_manifest
    SELECT
        ('01950000-000a-7100-8000-' || lpad(i::TEXT, 12, '0'))::UUID,
        ('01950000-0004-7100-8000-' || lpad(i::TEXT, 12, '0'))::UUID,
        '01950000-0006-7100-8000-000000000006',
        CASE WHEN i % 2 = 0 THEN 'Participação sintética confirmada.' ELSE NULL END,
        FALSE
    FROM generate_series(1, 12) generated(i);

    IF EXISTS (
        SELECT 1
        FROM presences presence_record
        JOIN fixture_presences_manifest manifest ON manifest.id = presence_record.id
        WHERE presence_record.member_id <> manifest.member_id
           OR presence_record.event_id <> manifest.event_id
    ) THEN
        RAISE EXCEPTION 'Development fixture Presence manifest collision';
    END IF;

    UPDATE presences presence_record
    SET deleted_at = v_now, deleted_by = NULL
    WHERE presence_record.deleted_at IS NULL
      AND EXISTS (
          SELECT 1
          FROM fixture_presences_manifest manifest
          WHERE manifest.member_id = presence_record.member_id
            AND manifest.event_id = presence_record.event_id
            AND manifest.id <> presence_record.id
      );

    INSERT INTO presences AS current_presence (
        id, member_id, event_id, observations,
        created_at, created_by, updated_at, updated_by, deleted_at, deleted_by
    )
    SELECT
        id, member_id, event_id, observations,
        v_now, NULL, v_now, NULL,
        CASE WHEN deleted THEN v_now ELSE NULL END, NULL
    FROM fixture_presences_manifest
    ON CONFLICT (id) DO UPDATE
    SET member_id = EXCLUDED.member_id,
        event_id = EXCLUDED.event_id,
        observations = EXCLUDED.observations,
        updated_at = CASE
            WHEN (
                current_presence.member_id,
                current_presence.event_id,
                current_presence.observations
            ) IS DISTINCT FROM (
                EXCLUDED.member_id,
                EXCLUDED.event_id,
                EXCLUDED.observations
            )
            OR (
                EXCLUDED.deleted_at IS NULL
                AND current_presence.deleted_at IS NOT NULL
            ) THEN v_now
            ELSE current_presence.updated_at
        END,
        updated_by = CASE
            WHEN (
                current_presence.member_id,
                current_presence.event_id,
                current_presence.observations
            ) IS DISTINCT FROM (
                EXCLUDED.member_id,
                EXCLUDED.event_id,
                EXCLUDED.observations
            )
            OR (
                EXCLUDED.deleted_at IS NULL
                AND current_presence.deleted_at IS NOT NULL
            ) THEN NULL
            ELSE current_presence.updated_by
        END,
        deleted_at = CASE
            WHEN EXCLUDED.deleted_at IS NULL THEN NULL
            ELSE COALESCE(current_presence.deleted_at, v_now)
        END,
        deleted_by = NULL
    WHERE (
        current_presence.member_id,
        current_presence.event_id,
        current_presence.observations,
        current_presence.deleted_at IS NOT NULL
    ) IS DISTINCT FROM (
        EXCLUDED.member_id,
        EXCLUDED.event_id,
        EXCLUDED.observations,
        EXCLUDED.deleted_at IS NOT NULL
    );

    -- Oratorianos: explicit workflow targets plus deterministic filler.
    CREATE TEMP TABLE fixture_oratorianos_manifest (
        id UUID PRIMARY KEY,
        first_name TEXT NOT NULL,
        surname TEXT NOT NULL,
        birth_date DATE,
        phone_number TEXT,
        name_key TEXT NOT NULL UNIQUE,
        deleted BOOLEAN NOT NULL,
        name_source_form_id UUID,
        name_source_signed_on DATE,
        birth_date_source_form_id UUID,
        birth_date_source_signed_on DATE,
        phone_source_form_id UUID,
        phone_source_signed_on DATE
    ) ON COMMIT DROP;

    INSERT INTO fixture_oratorianos_manifest (
        id, first_name, surname, birth_date, phone_number, name_key, deleted,
        name_source_form_id, name_source_signed_on,
        birth_date_source_form_id, birth_date_source_signed_on,
        phone_source_form_id, phone_source_signed_on
    )
    VALUES
        ('01950000-0008-7000-8000-000000000001',
         'Alice', 'Ferreira', DATE '2011-03-10', NULL, 'alice ferreira', FALSE,
         '01950000-000c-7000-8000-000000000002', v_today - 30,
         '01950000-000c-7000-8000-000000000002', v_today - 30,
         NULL, NULL),
        ('01950000-0008-7000-8000-000000000002',
         'Bruno', 'Costa', DATE '2009-07-22', '+5519998333002', 'bruno costa', FALSE,
         '01950000-000c-7000-8000-000000000003', v_today - 90,
         '01950000-000c-7000-8000-000000000003', v_today - 90,
         '01950000-000c-7000-8000-000000000003', v_today - 90),
        ('01950000-0008-7000-8000-000000000003',
         'Carla', 'D''Ávila', NULL, NULL, 'carla d''avila', FALSE,
         NULL, NULL, NULL, NULL, NULL, NULL),
        ('01950000-0008-7000-8000-000000000004',
         'Diego', 'Nunes', DATE '2010-01-09', NULL, 'diego nunes', FALSE,
         NULL, NULL, NULL, NULL, NULL, NULL),
        ('01950000-0008-7000-8000-000000000005',
         'Elisa', 'Martins', DATE '2000-05-20', '+5519998877665', 'elisa martins', FALSE,
         NULL, NULL, NULL, NULL, NULL, NULL),
        ('01950000-0008-7000-8000-000000000006',
         'Fábio', 'Souza', NULL, NULL, 'fabio souza', FALSE,
         NULL, NULL, NULL, NULL, NULL, NULL),
        ('01950000-0008-7000-8000-000000000007',
         'Giovana', 'Lima', DATE '2012-10-12', NULL, 'giovana lima', FALSE,
         NULL, NULL, NULL, NULL, NULL, NULL),
        ('01950000-0008-7000-8000-000000000008',
         'Heitor', 'Rocha', DATE '2008-04-11', '+5519998333008', 'heitor rocha', FALSE,
         '01950000-000c-7000-8000-000000000009', v_today - 45,
         '01950000-000c-7000-8000-000000000009', v_today - 45,
         '01950000-000c-7000-8000-000000000009', v_today - 45);

    INSERT INTO fixture_oratorianos_manifest (
        id, first_name, surname, birth_date, phone_number, name_key, deleted
    )
    SELECT
        ('01950000-0008-7100-8000-' || lpad(i::TEXT, 12, '0'))::UUID,
        (ARRAY[
            'Ana', 'Breno', 'Célia', 'Davi', 'Érica',
            'Iara', 'Joana', 'Kleber', 'Lúcia'
        ])[((i - 1) % 9) + 1],
        (ARRAY[
            'Almeida', 'Barbosa', 'Costa-Silva',
            'D''Ávila', 'Esteves', 'Ferreira'
        ])[((i - 1) / 9) + 1],
        CASE WHEN i % 3 = 0 THEN DATE '2007-01-01' + (i * 37) ELSE NULL END,
        CASE WHEN i % 4 = 0 THEN '+55199' || lpad((20000000 + i)::TEXT, 8, '0') ELSE NULL END,
        regexp_replace(
            normalize(lower(
                (ARRAY[
                    'Ana', 'Breno', 'Célia', 'Davi', 'Érica',
                    'Iara', 'Joana', 'Kleber', 'Lúcia'
                ])[((i - 1) % 9) + 1]
                    || ' '
                    || (ARRAY[
                        'Almeida', 'Barbosa', 'Costa-Silva',
                        'D''Ávila', 'Esteves', 'Ferreira'
                    ])[((i - 1) / 9) + 1]
            ), NFD),
            U&'[\0300-\036F]',
            '',
            'g'
        ),
        FALSE
    FROM generate_series(1, 53) generated(i);

    INSERT INTO fixture_oratorianos_manifest (
        id, first_name, surname, birth_date, phone_number, name_key, deleted
    )
    VALUES (
        '01950000-0008-7000-8000-000000000099',
        'Zuleica', 'Restaurável', DATE '2010-12-01', NULL,
        'zuleica restauravel', TRUE
    );

    -- The manifest UUID establishes fixture ownership. Mutable profile fields,
    -- including name_key, are reconciled below. An accepted name_key owned by
    -- another UUID remains a collision and must abort before any commit.
    IF EXISTS (
        SELECT 1
        FROM oratorianos person
        JOIN fixture_oratorianos_manifest manifest ON manifest.name_key = person.name_key
        WHERE person.id <> manifest.id
    ) THEN
        RAISE EXCEPTION 'Development fixture Oratoriano manifest collision';
    END IF;

    INSERT INTO oratorianos AS current_person (
        id, first_name, surname, birth_date, phone_number, name_key,
        name_source_form_id, name_source_signed_on, name_manual_updated_at,
        birth_date_source_form_id, birth_date_source_signed_on, birth_date_manual_updated_at,
        phone_source_form_id, phone_source_signed_on, phone_manual_updated_at,
        created_at, created_by, updated_at, updated_by, deleted_at, deleted_by
    )
    SELECT
        id, first_name, surname, birth_date, phone_number, name_key,
        name_source_form_id, name_source_signed_on, NULL,
        birth_date_source_form_id, birth_date_source_signed_on, NULL,
        phone_source_form_id, phone_source_signed_on, NULL,
        v_now, NULL, v_now, NULL,
        CASE WHEN deleted THEN v_now ELSE NULL END, NULL
    FROM fixture_oratorianos_manifest
    ON CONFLICT (id) DO UPDATE
    SET first_name = EXCLUDED.first_name,
        surname = EXCLUDED.surname,
        birth_date = EXCLUDED.birth_date,
        phone_number = EXCLUDED.phone_number,
        name_key = EXCLUDED.name_key,
        name_source_form_id = EXCLUDED.name_source_form_id,
        name_source_signed_on = EXCLUDED.name_source_signed_on,
        name_manual_updated_at = NULL,
        birth_date_source_form_id = EXCLUDED.birth_date_source_form_id,
        birth_date_source_signed_on = EXCLUDED.birth_date_source_signed_on,
        birth_date_manual_updated_at = NULL,
        phone_source_form_id = EXCLUDED.phone_source_form_id,
        phone_source_signed_on = EXCLUDED.phone_source_signed_on,
        phone_manual_updated_at = NULL,
        updated_at = CASE
            WHEN (
                current_person.first_name,
                current_person.surname,
                current_person.birth_date,
                current_person.phone_number,
                current_person.name_key,
                current_person.name_source_form_id,
                current_person.name_source_signed_on,
                current_person.name_manual_updated_at,
                current_person.birth_date_source_form_id,
                current_person.birth_date_source_signed_on,
                current_person.birth_date_manual_updated_at,
                current_person.phone_source_form_id,
                current_person.phone_source_signed_on,
                current_person.phone_manual_updated_at
            ) IS DISTINCT FROM (
                EXCLUDED.first_name,
                EXCLUDED.surname,
                EXCLUDED.birth_date,
                EXCLUDED.phone_number,
                EXCLUDED.name_key,
                EXCLUDED.name_source_form_id,
                EXCLUDED.name_source_signed_on,
                NULL,
                EXCLUDED.birth_date_source_form_id,
                EXCLUDED.birth_date_source_signed_on,
                NULL,
                EXCLUDED.phone_source_form_id,
                EXCLUDED.phone_source_signed_on,
                NULL
            )
            OR (
                EXCLUDED.deleted_at IS NULL
                AND current_person.deleted_at IS NOT NULL
            ) THEN v_now
            ELSE current_person.updated_at
        END,
        updated_by = CASE
            WHEN (
                current_person.first_name,
                current_person.surname,
                current_person.birth_date,
                current_person.phone_number,
                current_person.name_key,
                current_person.name_source_form_id,
                current_person.name_source_signed_on,
                current_person.name_manual_updated_at,
                current_person.birth_date_source_form_id,
                current_person.birth_date_source_signed_on,
                current_person.birth_date_manual_updated_at,
                current_person.phone_source_form_id,
                current_person.phone_source_signed_on,
                current_person.phone_manual_updated_at
            ) IS DISTINCT FROM (
                EXCLUDED.first_name,
                EXCLUDED.surname,
                EXCLUDED.birth_date,
                EXCLUDED.phone_number,
                EXCLUDED.name_key,
                EXCLUDED.name_source_form_id,
                EXCLUDED.name_source_signed_on,
                NULL,
                EXCLUDED.birth_date_source_form_id,
                EXCLUDED.birth_date_source_signed_on,
                NULL,
                EXCLUDED.phone_source_form_id,
                EXCLUDED.phone_source_signed_on,
                NULL
            )
            OR (
                EXCLUDED.deleted_at IS NULL
                AND current_person.deleted_at IS NOT NULL
            ) THEN NULL
            ELSE current_person.updated_by
        END,
        deleted_at = CASE
            WHEN EXCLUDED.deleted_at IS NULL THEN NULL
            ELSE COALESCE(current_person.deleted_at, v_now)
        END,
        deleted_by = NULL
    WHERE (
        current_person.first_name,
        current_person.surname,
        current_person.birth_date,
        current_person.phone_number,
        current_person.name_key,
        current_person.name_source_form_id,
        current_person.name_source_signed_on,
        current_person.name_manual_updated_at,
        current_person.birth_date_source_form_id,
        current_person.birth_date_source_signed_on,
        current_person.birth_date_manual_updated_at,
        current_person.phone_source_form_id,
        current_person.phone_source_signed_on,
        current_person.phone_manual_updated_at,
        current_person.deleted_at IS NOT NULL
    ) IS DISTINCT FROM (
        EXCLUDED.first_name,
        EXCLUDED.surname,
        EXCLUDED.birth_date,
        EXCLUDED.phone_number,
        EXCLUDED.name_key,
        EXCLUDED.name_source_form_id,
        EXCLUDED.name_source_signed_on,
        NULL,
        EXCLUDED.birth_date_source_form_id,
        EXCLUDED.birth_date_source_signed_on,
        NULL,
        EXCLUDED.phone_source_form_id,
        EXCLUDED.phone_source_signed_on,
        NULL,
        EXCLUDED.deleted_at IS NOT NULL
    );

    CREATE TEMP TABLE fixture_oratoriano_attendance_manifest (
        id UUID PRIMARY KEY,
        oratorio_id UUID NOT NULL,
        oratoriano_id UUID NOT NULL,
        registered_at TIMESTAMPTZ NOT NULL,
        deleted BOOLEAN NOT NULL,
        UNIQUE (oratorio_id, oratoriano_id)
    ) ON COMMIT DROP;

    INSERT INTO fixture_oratoriano_attendance_manifest
    VALUES
        ('01950000-000b-7000-8000-000000000001',
         '01950000-0006-7100-8000-000000000006',
         '01950000-0008-7000-8000-000000000001',
         (v_today - 35 + TIME '14:20') AT TIME ZONE 'America/Sao_Paulo', FALSE),
        ('01950000-000b-7000-8000-000000000002',
         '01950000-0006-7100-8000-000000000006',
         '01950000-0008-7000-8000-000000000002',
         (v_today - 35 + TIME '14:27') AT TIME ZONE 'America/Sao_Paulo', FALSE),
        ('01950000-000b-7000-8000-000000000003',
         '01950000-0006-7100-8000-000000000007',
         '01950000-0008-7000-8000-000000000001',
         (v_today - 370 + TIME '14:18') AT TIME ZONE 'America/Sao_Paulo', FALSE),
        ('01950000-000b-7000-8000-000000000004',
         '01950000-0006-7100-8000-000000000009',
         '01950000-0008-7000-8000-000000000003',
         (v_today - 20 + TIME '14:32') AT TIME ZONE 'America/Sao_Paulo', TRUE);

    IF EXISTS (
        SELECT 1
        FROM oratoriano_attendances attendance
        JOIN fixture_oratoriano_attendance_manifest manifest ON manifest.id = attendance.id
        WHERE attendance.oratorio_id <> manifest.oratorio_id
           OR attendance.oratoriano_id <> manifest.oratoriano_id
    ) THEN
        RAISE EXCEPTION 'Development fixture Oratoriano attendance manifest collision';
    END IF;

    UPDATE oratoriano_attendances attendance
    SET deleted_at = v_now, deleted_by = NULL
    WHERE attendance.deleted_at IS NULL
      AND EXISTS (
          SELECT 1
          FROM fixture_oratoriano_attendance_manifest manifest
          WHERE manifest.oratorio_id = attendance.oratorio_id
            AND manifest.oratoriano_id = attendance.oratoriano_id
            AND manifest.id <> attendance.id
      );

    INSERT INTO oratoriano_attendances AS current_attendance (
        id, oratorio_id, oratoriano_id, registered_at,
        created_at, created_by, updated_at, updated_by, deleted_at, deleted_by
    )
    SELECT
        id, oratorio_id, oratoriano_id, registered_at,
        v_now, NULL, v_now, NULL,
        CASE WHEN deleted THEN v_now ELSE NULL END, NULL
    FROM fixture_oratoriano_attendance_manifest
    ON CONFLICT (id) DO UPDATE
    SET oratorio_id = EXCLUDED.oratorio_id,
        oratoriano_id = EXCLUDED.oratoriano_id,
        registered_at = EXCLUDED.registered_at,
        updated_at = CASE
            WHEN (
                current_attendance.oratorio_id,
                current_attendance.oratoriano_id,
                current_attendance.registered_at
            ) IS DISTINCT FROM (
                EXCLUDED.oratorio_id,
                EXCLUDED.oratoriano_id,
                EXCLUDED.registered_at
            )
            OR (
                EXCLUDED.deleted_at IS NULL
                AND current_attendance.deleted_at IS NOT NULL
            ) THEN v_now
            ELSE current_attendance.updated_at
        END,
        updated_by = CASE
            WHEN (
                current_attendance.oratorio_id,
                current_attendance.oratoriano_id,
                current_attendance.registered_at
            ) IS DISTINCT FROM (
                EXCLUDED.oratorio_id,
                EXCLUDED.oratoriano_id,
                EXCLUDED.registered_at
            )
            OR (
                EXCLUDED.deleted_at IS NULL
                AND current_attendance.deleted_at IS NOT NULL
            ) THEN NULL
            ELSE current_attendance.updated_by
        END,
        deleted_at = CASE
            WHEN EXCLUDED.deleted_at IS NULL THEN NULL
            ELSE COALESCE(current_attendance.deleted_at, v_now)
        END,
        deleted_by = NULL
    WHERE (
        current_attendance.oratorio_id,
        current_attendance.oratoriano_id,
        current_attendance.registered_at,
        current_attendance.deleted_at IS NOT NULL
    ) IS DISTINCT FROM (
        EXCLUDED.oratorio_id,
        EXCLUDED.oratoriano_id,
        EXCLUDED.registered_at,
        EXCLUDED.deleted_at IS NOT NULL
    );

    -- Additional-form lifecycle and downloadable synthetic artifacts.
    v_completion_form_data := jsonb_build_object(
        'address', jsonb_build_object(
            'addressLine', 'Rua das Flores',
            'addressNumber', '150 fundos',
            'neighborhood', 'Centro',
            'cep', '13400000',
            'city', 'Piracicaba'
        ),
        'health', jsonb_build_object(
            'medicalFollowUp', jsonb_build_object('answer', 'NO'),
            'physicalActivityRestriction', jsonb_build_object('answer', 'NO'),
            'medicineUse', jsonb_build_object('answer', 'NO'),
            'allergies', jsonb_build_object('answer', 'NO'),
            'convulsions', jsonb_build_object('answer', 'NO'),
            'frequentFainting', jsonb_build_object('answer', 'NO'),
            'heartCondition', jsonb_build_object('answer', 'NO'),
            'otherHealthCondition', jsonb_build_object('answer', 'NO')
        ),
        'declarations', jsonb_build_object(
            'signerRelationshipConfirmed', TRUE,
            'informationTruthConfirmed', TRUE,
            'healthInformationCurrentConfirmed', TRUE,
            'informationUseUnderstood', TRUE,
            'formReviewed', TRUE,
            'imageAndVoiceAuthorizationAccepted', TRUE
        )
    );

    CREATE TEMP TABLE fixture_forms_manifest (
        id UUID PRIMARY KEY,
        oratoriano_id UUID NOT NULL,
        version INTEGER NOT NULL,
        status oratoriano_form_status_enum NOT NULL,
        origin oratoriano_form_origin_enum NOT NULL,
        draft_revision BIGINT NOT NULL,
        draft_data JSONB NOT NULL,
        signed_on DATE,
        completed_at TIMESTAMPTZ,
        revoked_at TIMESTAMPTZ,
        UNIQUE (oratoriano_id, version)
    ) ON COMMIT DROP;

    INSERT INTO fixture_forms_manifest
    VALUES
        ('01950000-000c-7000-8000-000000000001',
         '01950000-0008-7000-8000-000000000001', 1, 'SUPERSEDED',
         'PAPER_TRANSCRIPTION', 1,
         v_completion_form_data || jsonb_build_object(
             'firstName', 'Alice',
             'surname', 'Ferreira',
             'birthDate', '2011-03-10',
             'cpf', '52998224725',
             'schoolName', 'Escola Municipal de Piracicaba',
             'schoolGrade', '9º ano',
             'responsible', jsonb_build_object(
                 'relationship', 'MOTHER',
                 'firstName', 'Marina',
                 'surname', 'Ferreira',
                 'cpf', '11144477735',
                 'phoneNumber', '+5519998333011',
                 'email', 'marina.ferreira@example.com',
                 'atLeast18', TRUE
             ),
             'mother', jsonb_build_object(
                 'firstName', 'Marina',
                 'surname', 'Ferreira',
                 'cpf', '11144477735'
             ),
             'signedOn', (v_today - 180)::TEXT
         ),
         v_today - 180,
         (v_today - 170 + TIME '12:00') AT TIME ZONE 'America/Sao_Paulo', NULL),
        ('01950000-000c-7000-8000-000000000002',
         '01950000-0008-7000-8000-000000000001', 2, 'COMPLETED',
         'DIRECT_SYSTEM_ENTRY', 2,
         v_completion_form_data || jsonb_build_object(
             'firstName', 'Alice',
             'surname', 'Ferreira',
             'birthDate', '2011-03-10',
             'cpf', '52998224725',
             'schoolName', 'Escola Municipal de Piracicaba',
             'schoolGrade', '9º ano',
             'responsible', jsonb_build_object(
                 'relationship', 'MOTHER',
                 'firstName', 'Marina',
                 'surname', 'Ferreira',
                 'cpf', '11144477735',
                 'phoneNumber', '+5519998333011',
                 'email', 'marina.ferreira@example.com',
                 'atLeast18', TRUE
             ),
             'mother', jsonb_build_object(
                 'firstName', 'Marina',
                 'surname', 'Ferreira',
                 'cpf', '11144477735'
             ),
             'signedOn', (v_today - 30)::TEXT
         ),
         v_today - 30,
         (v_today - 25 + TIME '12:00') AT TIME ZONE 'America/Sao_Paulo', NULL),
        ('01950000-000c-7000-8000-000000000003',
         '01950000-0008-7000-8000-000000000002', 1, 'REVOKED',
         'PAPER_TRANSCRIPTION', 1,
         v_completion_form_data || jsonb_build_object(
             'firstName', 'Bruno',
             'surname', 'Costa',
             'birthDate', '2009-07-22',
             'cpf', '24681357928',
             'phoneNumber', '+5519998333002',
             'schoolName', 'Escola Comunitária Esperança',
             'schoolGrade', '2º ano do ensino médio',
             'responsible', jsonb_build_object(
                 'relationship', 'FATHER',
                 'firstName', 'Paulo',
                 'surname', 'Costa',
                 'cpf', '11144477735',
                 'phoneNumber', '+5519998333012',
                 'email', 'paulo.costa@example.com',
                 'atLeast18', TRUE
             ),
             'father', jsonb_build_object(
                 'firstName', 'Paulo',
                 'surname', 'Costa',
                 'cpf', '11144477735'
             ),
             'signedOn', (v_today - 90)::TEXT
         ),
         v_today - 90,
         (v_today - 85 + TIME '12:00') AT TIME ZONE 'America/Sao_Paulo',
         (v_today - 40 + TIME '12:00') AT TIME ZONE 'America/Sao_Paulo'),
        ('01950000-000c-7000-8000-000000000004',
         '01950000-0008-7000-8000-000000000003', 1, 'DRAFT',
         'DIRECT_SYSTEM_ENTRY', 3, '{"firstName":"Carla","surname":"D''Ávila"}'::JSONB,
         NULL, NULL, NULL),
        ('01950000-000c-7000-8000-000000000005',
         '01950000-0008-7000-8000-000000000004', 1, 'DRAFT',
         'PAPER_TRANSCRIPTION', 1, '{}'::JSONB, NULL, NULL, NULL),
        ('01950000-000c-7000-8000-000000000006',
         '01950000-0008-7000-8000-000000000005', 1, 'DRAFT',
         'DIRECT_SYSTEM_ENTRY', 4,
         v_completion_form_data || jsonb_build_object(
             'firstName', 'Elisa',
             'surname', 'Martins',
             'birthDate', '2000-05-20',
             'cpf', '52998224725',
             'phoneNumber', '+5519998877665',
             'responsible', jsonb_build_object(
                 'relationship', 'SELF',
                 'atLeast18', TRUE
             ),
             'signedOn', (v_today - 5)::TEXT
         ),
         v_today - 5, NULL, NULL),
        ('01950000-000c-7000-8000-000000000007',
         '01950000-0008-7000-8000-000000000006', 1, 'DRAFT',
         'PAPER_TRANSCRIPTION', 1, '{}'::JSONB, NULL, NULL, NULL),
        ('01950000-000c-7000-8000-000000000008',
         '01950000-0008-7000-8000-000000000007', 1, 'DRAFT',
         'DIRECT_SYSTEM_ENTRY', 1, '{}'::JSONB, NULL, NULL, NULL),
        ('01950000-000c-7000-8000-000000000009',
         '01950000-0008-7000-8000-000000000008', 1, 'COMPLETED',
         'PAPER_TRANSCRIPTION', 1,
         v_completion_form_data || jsonb_build_object(
             'firstName', 'Heitor',
             'surname', 'Rocha',
             'birthDate', '2008-04-11',
             'cpf', '11144477735',
             'phoneNumber', '+5519998333008',
             'responsible', jsonb_build_object(
                 'relationship', 'SELF',
                 'firstName', 'Heitor',
                 'surname', 'Rocha',
                 'cpf', '11144477735',
                 'phoneNumber', '+5519998333008',
                 'atLeast18', TRUE
             ),
             'signedOn', (v_today - 45)::TEXT
         ),
         v_today - 45,
         (v_today - 42 + TIME '12:00') AT TIME ZONE 'America/Sao_Paulo', NULL);

    IF EXISTS (
        SELECT 1
        FROM oratoriano_additional_forms form_record
        JOIN fixture_forms_manifest manifest ON manifest.id = form_record.id
        WHERE form_record.oratoriano_id <> manifest.oratoriano_id
           OR form_record.version <> manifest.version
    ) THEN
        RAISE EXCEPTION 'Development fixture additional-form manifest collision';
    END IF;

    INSERT INTO oratoriano_additional_forms AS current_form (
        id, oratoriano_id, version, status, origin, draft_revision, draft_data,
        signed_on, completed_at, completed_by, revoked_at, revoked_by,
        created_at, created_by, updated_at, updated_by, deleted_at, deleted_by
    )
    SELECT
        id, oratoriano_id, version, status, origin, draft_revision, draft_data,
        signed_on, completed_at,
        CASE WHEN completed_at IS NULL THEN NULL ELSE v_primary_coordinator END,
        revoked_at,
        CASE WHEN revoked_at IS NULL THEN NULL ELSE v_primary_coordinator END,
        v_now, NULL, v_now, NULL, NULL, NULL
    FROM fixture_forms_manifest
    ON CONFLICT (id) DO UPDATE
    SET oratoriano_id = EXCLUDED.oratoriano_id,
        version = EXCLUDED.version,
        status = EXCLUDED.status,
        origin = EXCLUDED.origin,
        draft_revision = EXCLUDED.draft_revision,
        draft_data = EXCLUDED.draft_data,
        signed_on = EXCLUDED.signed_on,
        completed_at = EXCLUDED.completed_at,
        completed_by = EXCLUDED.completed_by,
        revoked_at = EXCLUDED.revoked_at,
        revoked_by = EXCLUDED.revoked_by,
        updated_at = v_now,
        updated_by = NULL,
        deleted_at = NULL,
        deleted_by = NULL
    WHERE (
        current_form.oratoriano_id,
        current_form.version,
        current_form.status,
        current_form.origin,
        current_form.draft_revision,
        current_form.draft_data,
        current_form.signed_on,
        current_form.completed_at,
        current_form.completed_by,
        current_form.revoked_at,
        current_form.revoked_by,
        current_form.deleted_at,
        current_form.deleted_by
    ) IS DISTINCT FROM (
        EXCLUDED.oratoriano_id,
        EXCLUDED.version,
        EXCLUDED.status,
        EXCLUDED.origin,
        EXCLUDED.draft_revision,
        EXCLUDED.draft_data,
        EXCLUDED.signed_on,
        EXCLUDED.completed_at,
        EXCLUDED.completed_by,
        EXCLUDED.revoked_at,
        EXCLUDED.revoked_by,
        NULL,
        NULL
    );

    CREATE TEMP TABLE fixture_print_snapshots_manifest (
        id UUID PRIMARY KEY,
        form_id UUID NOT NULL,
        draft_revision BIGINT NOT NULL,
        mode oratoriano_form_print_mode_enum NOT NULL,
        generated_at TIMESTAMPTZ NOT NULL,
        captured_data JSONB NOT NULL
    ) ON COMMIT DROP;

    INSERT INTO fixture_print_snapshots_manifest
    SELECT
        '01950000-000f-7000-8000-000000000001'::UUID,
        '01950000-000c-7000-8000-000000000006'::UUID,
        4,
        'PREFILLED'::oratoriano_form_print_mode_enum,
        (v_today - 6 + TIME '12:00') AT TIME ZONE 'America/Sao_Paulo',
        draft_data
    FROM fixture_forms_manifest
    WHERE id = '01950000-000c-7000-8000-000000000006'
    UNION ALL
    SELECT
        '01950000-000f-7000-8000-000000000002'::UUID,
        '01950000-000c-7000-8000-000000000007'::UUID,
        1,
        'IDENTIFIED_BLANK'::oratoriano_form_print_mode_enum,
        (v_today - 1 + TIME '12:00') AT TIME ZONE 'America/Sao_Paulo',
        '{}'::JSONB
    UNION ALL
    SELECT
        mapping.snapshot_id,
        form_record.id,
        form_record.draft_revision,
        mapping.mode::oratoriano_form_print_mode_enum,
        (v_today - mapping.generated_days_before + TIME '12:00')
            AT TIME ZONE 'America/Sao_Paulo',
        CASE
            WHEN mapping.mode = 'PREFILLED' THEN form_record.draft_data
            ELSE '{}'::JSONB
        END
    FROM fixture_forms_manifest form_record
    JOIN (
        VALUES
            ('01950000-000f-7000-8000-000000000003'::UUID,
             '01950000-000c-7000-8000-000000000001'::UUID,
             'IDENTIFIED_BLANK', 181),
            ('01950000-000f-7000-8000-000000000004'::UUID,
             '01950000-000c-7000-8000-000000000002'::UUID,
             'PREFILLED', 31),
            ('01950000-000f-7000-8000-000000000005'::UUID,
             '01950000-000c-7000-8000-000000000003'::UUID,
             'IDENTIFIED_BLANK', 91),
            ('01950000-000f-7000-8000-000000000006'::UUID,
             '01950000-000c-7000-8000-000000000009'::UUID,
             'IDENTIFIED_BLANK', 46)
    ) AS mapping(snapshot_id, form_id, mode, generated_days_before)
      ON mapping.form_id = form_record.id;

    IF EXISTS (
        SELECT 1
        FROM oratoriano_form_print_snapshots snapshot
        JOIN fixture_print_snapshots_manifest manifest ON manifest.id = snapshot.id
        WHERE snapshot.form_id <> manifest.form_id
    ) THEN
        RAISE EXCEPTION 'Development fixture print-snapshot manifest collision';
    END IF;

    -- Direct-entry completion owns the latest active print snapshot of this
    -- completion-ready draft. Only that explicitly designated collection is
    -- reset; snapshots for every other form remain Developer-owned unless
    -- their UUID appears in the canonical manifest.
    UPDATE oratoriano_form_print_snapshots
    SET deleted_at = v_now,
        deleted_by = NULL
    WHERE form_id = '01950000-000c-7000-8000-000000000006'
      AND id <> '01950000-000f-7000-8000-000000000001'
      AND deleted_at IS NULL;

    INSERT INTO oratoriano_form_print_snapshots AS current_snapshot (
        id, form_id, draft_revision, mode, generated_at, template_version,
        page_count, captured_data, fingerprint,
        created_at, created_by, updated_at, updated_by, deleted_at, deleted_by
    )
    SELECT
        id, form_id, draft_revision, mode,
        generated_at,
        'development-fixture-v1', 1, captured_data,
        encode(sha256(convert_to(captured_data::TEXT, 'UTF8')), 'hex'),
        v_now, NULL, v_now, NULL, NULL, NULL
    FROM fixture_print_snapshots_manifest
    ON CONFLICT (id) DO UPDATE
    SET form_id = EXCLUDED.form_id,
        draft_revision = EXCLUDED.draft_revision,
        mode = EXCLUDED.mode,
        generated_at = EXCLUDED.generated_at,
        template_version = EXCLUDED.template_version,
        page_count = EXCLUDED.page_count,
        captured_data = EXCLUDED.captured_data,
        fingerprint = EXCLUDED.fingerprint,
        updated_at = v_now,
        updated_by = NULL,
        deleted_at = NULL,
        deleted_by = NULL
    WHERE (
        current_snapshot.form_id,
        current_snapshot.draft_revision,
        current_snapshot.mode,
        current_snapshot.generated_at,
        current_snapshot.template_version,
        current_snapshot.page_count,
        current_snapshot.captured_data,
        current_snapshot.fingerprint,
        current_snapshot.deleted_at,
        current_snapshot.deleted_by
    ) IS DISTINCT FROM (
        EXCLUDED.form_id,
        EXCLUDED.draft_revision,
        EXCLUDED.mode,
        EXCLUDED.generated_at,
        EXCLUDED.template_version,
        EXCLUDED.page_count,
        EXCLUDED.captured_data,
        EXCLUDED.fingerprint,
        NULL,
        NULL
    );

    CREATE TEMP TABLE fixture_attachments_manifest (
        id UUID PRIMARY KEY,
        form_id UUID NOT NULL,
        snapshot_id UUID NOT NULL,
        signed_on DATE NOT NULL,
        original_filename TEXT NOT NULL
    ) ON COMMIT DROP;

    INSERT INTO fixture_attachments_manifest
    VALUES
        ('01950000-000d-7000-8000-000000000001',
         '01950000-000c-7000-8000-000000000006',
         '01950000-000f-7000-8000-000000000001',
         v_today - 5,
         'formulario-sintetico-desenvolvimento.pdf'),
        ('01950000-000d-7000-8000-000000000002',
         '01950000-000c-7000-8000-000000000001',
         '01950000-000f-7000-8000-000000000003',
         v_today - 180,
         'formulario-sintetico-historico-1.pdf'),
        ('01950000-000d-7000-8000-000000000003',
         '01950000-000c-7000-8000-000000000002',
         '01950000-000f-7000-8000-000000000004',
         v_today - 30,
         'formulario-sintetico-historico-2.pdf'),
        ('01950000-000d-7000-8000-000000000004',
         '01950000-000c-7000-8000-000000000003',
         '01950000-000f-7000-8000-000000000005',
         v_today - 90,
         'formulario-sintetico-historico-3.pdf'),
        ('01950000-000d-7000-8000-000000000005',
         '01950000-000c-7000-8000-000000000009',
         '01950000-000f-7000-8000-000000000006',
         v_today - 45,
         'formulario-sintetico-historico-9.pdf');

    IF EXISTS (
        SELECT 1
        FROM oratoriano_form_attachments attachment
        JOIN fixture_attachments_manifest manifest ON manifest.id = attachment.id
        WHERE attachment.form_id <> manifest.form_id
    ) THEN
        RAISE EXCEPTION 'Development fixture signed-attachment manifest collision';
    END IF;

    -- Upload replacement owns the active attachment collection of the
    -- completion-ready draft. Only that explicitly designated collection is
    -- reset; attachments of every other form remain Developer-owned unless
    -- their UUID appears in the canonical manifest.
    UPDATE oratoriano_form_attachments
    SET deleted_at = v_now,
        deleted_by = NULL
    WHERE form_id = '01950000-000c-7000-8000-000000000006'
      AND id <> '01950000-000d-7000-8000-000000000001'
      AND deleted_at IS NULL;

    INSERT INTO oratoriano_form_attachments AS current_attachment (
        id, form_id, original_filename, verified_mime_type,
        byte_length, page_order, page_count, sha256, bytes,
        created_at, created_by, updated_at, updated_by, deleted_at, deleted_by
    )
    SELECT
        id,
        form_id,
        original_filename,
        'application/pdf',
        octet_length(generated.pdf_bytes),
        1,
        1,
        encode(sha256(generated.pdf_bytes), 'hex'),
        generated.pdf_bytes,
        v_now, NULL, v_now, NULL, NULL, NULL
    FROM fixture_attachments_manifest
    CROSS JOIN LATERAL (
        SELECT convert_to(
            replace(
                replace(
                    replace(
                        convert_from(decode(v_pdf_base64, 'base64'), 'UTF8'),
                        'FORM_UUID_00000000000000000000000000',
                        form_id::TEXT
                    ),
                    'SNAPSHOT_UUID_0000000000000000000000',
                    snapshot_id::TEXT
                ),
                'DATE_TOKEN',
                signed_on::TEXT
            ),
            'UTF8'
        ) AS pdf_bytes
    ) generated
    ON CONFLICT (id) DO UPDATE
    SET form_id = EXCLUDED.form_id,
        original_filename = EXCLUDED.original_filename,
        verified_mime_type = EXCLUDED.verified_mime_type,
        byte_length = EXCLUDED.byte_length,
        page_order = EXCLUDED.page_order,
        page_count = EXCLUDED.page_count,
        sha256 = EXCLUDED.sha256,
        bytes = EXCLUDED.bytes,
        updated_at = v_now,
        updated_by = NULL,
        deleted_at = NULL,
        deleted_by = NULL
    WHERE (
        current_attachment.form_id,
        current_attachment.original_filename,
        current_attachment.verified_mime_type,
        current_attachment.byte_length,
        current_attachment.page_order,
        current_attachment.page_count,
        current_attachment.sha256,
        current_attachment.bytes,
        current_attachment.deleted_at,
        current_attachment.deleted_by
    ) IS DISTINCT FROM (
        EXCLUDED.form_id,
        EXCLUDED.original_filename,
        EXCLUDED.verified_mime_type,
        EXCLUDED.byte_length,
        EXCLUDED.page_order,
        EXCLUDED.page_count,
        EXCLUDED.sha256,
        EXCLUDED.bytes,
        NULL,
        NULL
    );
END
$development_fixture$;
