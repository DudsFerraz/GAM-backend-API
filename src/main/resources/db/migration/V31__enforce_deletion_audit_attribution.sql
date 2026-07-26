ALTER TABLE accounts
    DROP CONSTRAINT fk_account_created_by,
    DROP CONSTRAINT fk_account_updated_by,
    DROP CONSTRAINT fk_account_deleted_by;

ALTER TABLE accounts
    ADD CONSTRAINT fk_account_created_by
        FOREIGN KEY (created_by) REFERENCES accounts(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_account_updated_by
        FOREIGN KEY (updated_by) REFERENCES accounts(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_account_deleted_by
        FOREIGN KEY (deleted_by) REFERENCES accounts(id) ON DELETE SET NULL,
    ADD CONSTRAINT check_accounts_deleted_attribution
        CHECK (deleted_by IS NULL OR deleted_at IS NOT NULL);

ALTER TABLE roles
    ADD CONSTRAINT check_roles_deleted_attribution
        CHECK (deleted_by IS NULL OR deleted_at IS NOT NULL);

ALTER TABLE permissions
    ADD CONSTRAINT check_permissions_deleted_attribution
        CHECK (deleted_by IS NULL OR deleted_at IS NOT NULL);

ALTER TABLE role_permissions
    ADD CONSTRAINT check_role_permissions_deleted_attribution
        CHECK (deleted_by IS NULL OR deleted_at IS NOT NULL);

ALTER TABLE account_roles
    ADD CONSTRAINT check_account_roles_deleted_attribution
        CHECK (deleted_by IS NULL OR deleted_at IS NOT NULL);

ALTER TABLE members
    ADD CONSTRAINT check_members_deleted_attribution
        CHECK (deleted_by IS NULL OR deleted_at IS NOT NULL);

ALTER TABLE gam_locations
    ADD CONSTRAINT check_gam_locations_deleted_attribution
        CHECK (deleted_by IS NULL OR deleted_at IS NOT NULL);

ALTER TABLE events
    ADD CONSTRAINT check_events_deleted_attribution
        CHECK (deleted_by IS NULL OR deleted_at IS NOT NULL);

ALTER TABLE presences
    ADD CONSTRAINT check_presences_deleted_attribution
        CHECK (deleted_by IS NULL OR deleted_at IS NOT NULL);

ALTER TABLE oratorios
    ADD CONSTRAINT check_oratorios_deleted_attribution
        CHECK (deleted_by IS NULL OR deleted_at IS NOT NULL);

ALTER TABLE oratorianos
    ADD CONSTRAINT check_oratorianos_deleted_attribution
        CHECK (deleted_by IS NULL OR deleted_at IS NOT NULL);

ALTER TABLE missas
    ADD CONSTRAINT check_missas_deleted_attribution
        CHECK (deleted_by IS NULL OR deleted_at IS NOT NULL);

ALTER TABLE membership_solicitations
    ADD CONSTRAINT check_membership_solicitations_deleted_attribution
        CHECK (deleted_by IS NULL OR deleted_at IS NOT NULL);

ALTER TABLE oratoriano_attendances
    ADD CONSTRAINT check_oratoriano_attendances_deleted_attribution
        CHECK (deleted_by IS NULL OR deleted_at IS NOT NULL);

ALTER TABLE oratoriano_additional_forms
    ADD CONSTRAINT check_oratoriano_additional_forms_deleted_attribution
        CHECK (deleted_by IS NULL OR deleted_at IS NOT NULL);

ALTER TABLE oratoriano_form_print_snapshots
    ADD CONSTRAINT check_oratoriano_form_print_snapshots_deleted_attribution
        CHECK (deleted_by IS NULL OR deleted_at IS NOT NULL);

ALTER TABLE oratoriano_form_attachments
    ADD CONSTRAINT check_oratoriano_form_attachments_deleted_attribution
        CHECK (deleted_by IS NULL OR deleted_at IS NOT NULL);

ALTER TABLE roles
    DROP CONSTRAINT fk_roles_created_by,
    DROP CONSTRAINT fk_roles_updated_by,
    DROP CONSTRAINT fk_roles_deleted_by,
    ADD CONSTRAINT fk_roles_created_by
        FOREIGN KEY (created_by) REFERENCES accounts(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_roles_updated_by
        FOREIGN KEY (updated_by) REFERENCES accounts(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_roles_deleted_by
        FOREIGN KEY (deleted_by) REFERENCES accounts(id) ON DELETE SET NULL;

ALTER TABLE permissions
    DROP CONSTRAINT fk_permissions_created_by,
    DROP CONSTRAINT fk_permissions_updated_by,
    DROP CONSTRAINT fk_permissions_deleted_by,
    ADD CONSTRAINT fk_permissions_created_by
        FOREIGN KEY (created_by) REFERENCES accounts(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_permissions_updated_by
        FOREIGN KEY (updated_by) REFERENCES accounts(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_permissions_deleted_by
        FOREIGN KEY (deleted_by) REFERENCES accounts(id) ON DELETE SET NULL;

ALTER TABLE role_permissions
    DROP CONSTRAINT fk_role_perm_created_by,
    DROP CONSTRAINT fk_role_perm_deleted_by,
    ADD CONSTRAINT fk_role_perm_created_by
        FOREIGN KEY (created_by) REFERENCES accounts(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_role_perm_deleted_by
        FOREIGN KEY (deleted_by) REFERENCES accounts(id) ON DELETE SET NULL;

ALTER TABLE members
    DROP CONSTRAINT fk_member_created_by,
    DROP CONSTRAINT fk_member_updated_by,
    DROP CONSTRAINT fk_member_deleted_by,
    ADD CONSTRAINT fk_member_created_by
        FOREIGN KEY (created_by) REFERENCES accounts(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_member_updated_by
        FOREIGN KEY (updated_by) REFERENCES accounts(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_member_deleted_by
        FOREIGN KEY (deleted_by) REFERENCES accounts(id) ON DELETE SET NULL;

ALTER TABLE gam_locations
    DROP CONSTRAINT fk_location_created_by,
    DROP CONSTRAINT fk_location_updated_by,
    DROP CONSTRAINT fk_location_deleted_by,
    ADD CONSTRAINT fk_location_created_by
        FOREIGN KEY (created_by) REFERENCES accounts(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_location_updated_by
        FOREIGN KEY (updated_by) REFERENCES accounts(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_location_deleted_by
        FOREIGN KEY (deleted_by) REFERENCES accounts(id) ON DELETE SET NULL;

ALTER TABLE events
    DROP CONSTRAINT fk_event_created_by,
    DROP CONSTRAINT fk_event_updated_by,
    DROP CONSTRAINT fk_event_deleted_by,
    ADD CONSTRAINT fk_event_created_by
        FOREIGN KEY (created_by) REFERENCES accounts(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_event_updated_by
        FOREIGN KEY (updated_by) REFERENCES accounts(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_event_deleted_by
        FOREIGN KEY (deleted_by) REFERENCES accounts(id) ON DELETE SET NULL;

ALTER TABLE presences
    DROP CONSTRAINT fk_presence_created_by,
    DROP CONSTRAINT fk_presence_updated_by,
    DROP CONSTRAINT fk_presence_deleted_by,
    ADD CONSTRAINT fk_presence_created_by
        FOREIGN KEY (created_by) REFERENCES accounts(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_presence_updated_by
        FOREIGN KEY (updated_by) REFERENCES accounts(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_presence_deleted_by
        FOREIGN KEY (deleted_by) REFERENCES accounts(id) ON DELETE SET NULL;

ALTER TABLE oratorios
    DROP CONSTRAINT fk_oratorio_created_by,
    DROP CONSTRAINT fk_oratorio_updated_by,
    DROP CONSTRAINT fk_oratorio_deleted_by,
    ADD CONSTRAINT fk_oratorio_created_by
        FOREIGN KEY (created_by) REFERENCES accounts(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_oratorio_updated_by
        FOREIGN KEY (updated_by) REFERENCES accounts(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_oratorio_deleted_by
        FOREIGN KEY (deleted_by) REFERENCES accounts(id) ON DELETE SET NULL;

ALTER TABLE oratorianos
    DROP CONSTRAINT fk_oratoriano_created_by,
    DROP CONSTRAINT fk_oratoriano_updated_by,
    DROP CONSTRAINT fk_oratoriano_deleted_by,
    ADD CONSTRAINT fk_oratoriano_created_by
        FOREIGN KEY (created_by) REFERENCES accounts(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_oratoriano_updated_by
        FOREIGN KEY (updated_by) REFERENCES accounts(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_oratoriano_deleted_by
        FOREIGN KEY (deleted_by) REFERENCES accounts(id) ON DELETE SET NULL;

ALTER TABLE missas
    DROP CONSTRAINT fk_missa_created_by,
    DROP CONSTRAINT fk_missa_updated_by,
    DROP CONSTRAINT fk_missa_deleted_by,
    ADD CONSTRAINT fk_missa_created_by
        FOREIGN KEY (created_by) REFERENCES accounts(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_missa_updated_by
        FOREIGN KEY (updated_by) REFERENCES accounts(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_missa_deleted_by
        FOREIGN KEY (deleted_by) REFERENCES accounts(id) ON DELETE SET NULL;

ALTER TABLE membership_solicitations
    DROP CONSTRAINT fk_membership_solicitations_created_by,
    DROP CONSTRAINT fk_membership_solicitations_updated_by,
    DROP CONSTRAINT fk_membership_solicitations_deleted_by,
    ADD CONSTRAINT fk_membership_solicitations_created_by
        FOREIGN KEY (created_by) REFERENCES accounts(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_membership_solicitations_updated_by
        FOREIGN KEY (updated_by) REFERENCES accounts(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_membership_solicitations_deleted_by
        FOREIGN KEY (deleted_by) REFERENCES accounts(id) ON DELETE SET NULL;

ALTER TABLE oratorio_team_assignments
    DROP CONSTRAINT fk_oratorio_team_assignment_created_by,
    ADD CONSTRAINT fk_oratorio_team_assignment_created_by
        FOREIGN KEY (created_by) REFERENCES accounts(id) ON DELETE SET NULL;

ALTER TABLE oratoriano_attendances
    DROP CONSTRAINT fk_oratoriano_attendance_created_by,
    DROP CONSTRAINT fk_oratoriano_attendance_updated_by,
    DROP CONSTRAINT fk_oratoriano_attendance_deleted_by,
    ADD CONSTRAINT fk_oratoriano_attendance_created_by
        FOREIGN KEY (created_by) REFERENCES accounts(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_oratoriano_attendance_updated_by
        FOREIGN KEY (updated_by) REFERENCES accounts(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_oratoriano_attendance_deleted_by
        FOREIGN KEY (deleted_by) REFERENCES accounts(id) ON DELETE SET NULL;

ALTER TABLE oratoriano_additional_forms
    DROP CONSTRAINT fk_oratoriano_additional_form_created_by,
    DROP CONSTRAINT fk_oratoriano_additional_form_updated_by,
    DROP CONSTRAINT fk_oratoriano_additional_form_deleted_by,
    ADD CONSTRAINT fk_oratoriano_additional_form_created_by
        FOREIGN KEY (created_by) REFERENCES accounts(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_oratoriano_additional_form_updated_by
        FOREIGN KEY (updated_by) REFERENCES accounts(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_oratoriano_additional_form_deleted_by
        FOREIGN KEY (deleted_by) REFERENCES accounts(id) ON DELETE SET NULL;

ALTER TABLE oratoriano_form_print_snapshots
    DROP CONSTRAINT fk_oratoriano_form_print_snapshot_created_by,
    DROP CONSTRAINT fk_oratoriano_form_print_snapshot_updated_by,
    DROP CONSTRAINT fk_oratoriano_form_print_snapshot_deleted_by,
    ADD CONSTRAINT fk_oratoriano_form_print_snapshot_created_by
        FOREIGN KEY (created_by) REFERENCES accounts(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_oratoriano_form_print_snapshot_updated_by
        FOREIGN KEY (updated_by) REFERENCES accounts(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_oratoriano_form_print_snapshot_deleted_by
        FOREIGN KEY (deleted_by) REFERENCES accounts(id) ON DELETE SET NULL;

ALTER TABLE oratoriano_form_attachments
    DROP CONSTRAINT fk_oratoriano_form_attachment_created_by,
    DROP CONSTRAINT fk_oratoriano_form_attachment_updated_by,
    DROP CONSTRAINT fk_oratoriano_form_attachment_deleted_by,
    ADD CONSTRAINT fk_oratoriano_form_attachment_created_by
        FOREIGN KEY (created_by) REFERENCES accounts(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_oratoriano_form_attachment_updated_by
        FOREIGN KEY (updated_by) REFERENCES accounts(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_oratoriano_form_attachment_deleted_by
        FOREIGN KEY (deleted_by) REFERENCES accounts(id) ON DELETE SET NULL;

ALTER TABLE activity_logs
    DROP CONSTRAINT fk_activity_logs_actor_account;
