package br.org.gam.api.shared.auditing;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.mapstruct.Mapping;

@Retention(RetentionPolicy.CLASS)
@Mapping(target = "createdAt", ignore = true)
@Mapping(target = "createdBy", ignore = true)
@Mapping(target = "updatedAt", ignore = true)
@Mapping(target = "updatedBy", ignore = true)
@Mapping(target = "deletedAt", ignore = true)
@Mapping(target = "deletedBy", ignore = true)
public @interface IgnoreFullAuditFields {
}
