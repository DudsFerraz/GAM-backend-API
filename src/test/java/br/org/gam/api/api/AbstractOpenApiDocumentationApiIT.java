package br.org.gam.api.api;

import br.org.gam.api.account.application.useCases.GetAccount;
import br.org.gam.api.account.application.useCases.SearchAccounts;
import br.org.gam.api.account.application.useCases.getCurrentAccountContext.GetCurrentAccountContext;
import br.org.gam.api.account.application.useCases.loginAccount.LoginAccount;
import br.org.gam.api.account.application.useCases.registerAccount.RegisterAccount;
import br.org.gam.api.account.web.AccountController;
import br.org.gam.api.event.application.useCases.GetEvent;
import br.org.gam.api.event.application.useCases.SearchEvents;
import br.org.gam.api.event.application.useCases.createEvent.CreateEvent;
import br.org.gam.api.event.application.useCases.manageEvent.ManageGenericEvent;
import br.org.gam.api.event.oratorio.application.useCases.OratorioOperations;
import br.org.gam.api.event.oratorio.web.OratorioController;
import br.org.gam.api.event.web.EventController;
import br.org.gam.api.gamLocation.application.useCases.CreateGamLocation;
import br.org.gam.api.gamLocation.application.useCases.GetGamLocations;
import br.org.gam.api.gamLocation.application.useCases.RemoveGamLocation;
import br.org.gam.api.gamLocation.application.useCases.UpdateGamLocation;
import br.org.gam.api.gamLocation.web.GamLocationController;
import br.org.gam.api.health.application.HealthReadiness;
import br.org.gam.api.health.web.HealthController;
import br.org.gam.api.member.application.useCases.Activation;
import br.org.gam.api.member.application.useCases.GetMember;
import br.org.gam.api.member.application.useCases.SearchMembers;
import br.org.gam.api.member.application.useCases.registerMember.RegisterMemberWorkflow;
import br.org.gam.api.member.solicitation.application.useCases.GetMembershipSolicitation;
import br.org.gam.api.member.solicitation.application.useCases.ReviewMembershipSolicitation;
import br.org.gam.api.member.solicitation.application.useCases.SearchMembershipSolicitations;
import br.org.gam.api.member.solicitation.application.useCases.SubmitMembershipSolicitation;
import br.org.gam.api.member.solicitation.web.MembershipSolicitationController;
import br.org.gam.api.member.web.MemberController;
import br.org.gam.api.oratoriano.application.useCases.OratorianoForms;
import br.org.gam.api.oratoriano.application.useCases.OratorianoRecords;
import br.org.gam.api.oratoriano.web.OratorianoController;
import br.org.gam.api.oratoriano.web.OratorianoFormController;
import br.org.gam.api.presence.application.useCases.GetPresence;
import br.org.gam.api.presence.application.useCases.registerPresence.RegisterPresence;
import br.org.gam.api.rbac.accountRole.application.useCases.AddAccountRole;
import br.org.gam.api.rbac.accountRole.application.useCases.DropAccountRole;
import br.org.gam.api.rbac.accountRole.application.useCases.GetAccountRoleAssignment;
import br.org.gam.api.rbac.accountRole.application.useCases.GetAccountRoles;
import br.org.gam.api.rbac.accountRole.web.AccountRoleController;
import br.org.gam.api.rbac.permission.application.useCases.GetPermission;
import br.org.gam.api.rbac.permission.web.PermissionController;
import br.org.gam.api.rbac.role.application.RoleEntityLoader;
import br.org.gam.api.rbac.role.application.useCases.GetRole;
import br.org.gam.api.rbac.role.application.useCases.getRolePermissions.GetRolePermissions;
import br.org.gam.api.rbac.role.web.RoleController;
import br.org.gam.api.security.jwt.JwtAuthFilter;
import br.org.gam.api.security.refreshtoken.application.RefreshTokenService;
import br.org.gam.api.security.web.AuthController;
import br.org.gam.api.shared.openapi.OpenApiConfig;
import br.org.gam.api.shared.web.PaginationWebConfiguration;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.configuration.SpringDocPageableConfiguration;
import org.springdoc.core.configuration.SpringDocSecurityConfiguration;
import org.springdoc.core.configuration.SpringDocSortConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springdoc.webmvc.ui.SwaggerConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@ContextConfiguration(classes = AbstractOpenApiDocumentationApiIT.OpenApiWebTestConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(
        controllers = {
                AccountController.class,
                EventController.class,
                OratorioController.class,
                GamLocationController.class,
                MemberController.class,
                MembershipSolicitationController.class,
                OratorianoController.class,
                OratorianoFormController.class,
                AccountRoleController.class,
                PermissionController.class,
                RoleController.class,
                AuthController.class,
                HealthController.class
        },
        excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class,
                UserDetailsServiceAutoConfiguration.class
        },
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class)
)
@Import({
        OpenApiConfig.class,
        PaginationWebConfiguration.class,
        AccountController.class,
        EventController.class,
        OratorioController.class,
        GamLocationController.class,
        MemberController.class,
        MembershipSolicitationController.class,
        OratorianoController.class,
        OratorianoFormController.class,
        AccountRoleController.class,
        PermissionController.class,
        RoleController.class,
        AuthController.class,
        HealthController.class
})
@ImportAutoConfiguration({
        SpringDocConfiguration.class,
        SpringDocPageableConfiguration.class,
        SpringDocSecurityConfiguration.class,
        SpringDocSortConfiguration.class,
        SpringDocConfigProperties.class,
        SpringDocWebMvcConfiguration.class,
        SwaggerConfig.class,
        SwaggerUiConfigProperties.class,
        SwaggerUiOAuthProperties.class
})
@MockitoBean(types = {
        GetAccount.class,
        GetCurrentAccountContext.class,
        SearchAccounts.class,
        CreateEvent.class,
        GetEvent.class,
        SearchEvents.class,
        GetPresence.class,
        ManageGenericEvent.class,
        RegisterPresence.class,
        OratorioOperations.class,
        CreateGamLocation.class,
        GetGamLocations.class,
        UpdateGamLocation.class,
        RemoveGamLocation.class,
        RegisterMemberWorkflow.class,
        GetMember.class,
        SearchMembers.class,
        Activation.class,
        OratorianoRecords.class,
        OratorianoForms.class,
        SubmitMembershipSolicitation.class,
        GetMembershipSolicitation.class,
        SearchMembershipSolicitations.class,
        ReviewMembershipSolicitation.class,
        GetAccountRoles.class,
        AddAccountRole.class,
        DropAccountRole.class,
        GetAccountRoleAssignment.class,
        GetPermission.class,
        GetRole.class,
        GetRolePermissions.class,
        RoleEntityLoader.class,
        RegisterAccount.class,
        LoginAccount.class,
        RefreshTokenService.class,
        HealthReadiness.class
})
abstract class AbstractOpenApiDocumentationApiIT {

    private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() {
    };

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    protected void assertHtmlEndpointAvailable(String path) {
        try {
            MvcResult result = mockMvc.perform(get(path).accept(MediaType.TEXT_HTML)).andReturn();
            if (result.getResponse().getStatus() / 100 == 3) {
                String redirect = Objects.requireNonNull(
                        result.getResponse().getRedirectedUrl(),
                        "Expected a redirect URL for a redirect response"
                );
                result = mockMvc.perform(get(redirect).accept(MediaType.TEXT_HTML)).andReturn();
            }
            status().isOk().match(result);
            content().contentTypeCompatibleWith(MediaType.TEXT_HTML).match(result);
        } catch (Exception exception) {
            throw new AssertionError("Expected an available HTML endpoint at " + path, exception);
        }
    }

    protected Map<String, Object> swaggerUiConfiguration() {
        return getJson("/openapi.json/swagger-config");
    }

    protected OpenApiResponse openApiContract() {
        return new OpenApiResponse(getJson("/openapi.json"));
    }

    private Map<String, Object> getJson(String path) {
        try {
            MvcResult result = mockMvc.perform(get(path).accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andReturn();
            return objectMapper.readValue(result.getResponse().getContentAsByteArray(), JSON_OBJECT);
        } catch (Exception exception) {
            throw new AssertionError("Expected an available JSON endpoint at " + path, exception);
        }
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> object(Map<String, Object> source, String property) {
        return (Map<String, Object>) source.get(property);
    }

    @SuppressWarnings("unchecked")
    protected List<Map<String, Object>> objects(Map<String, Object> source, String property) {
        return (List<Map<String, Object>>) source.get(property);
    }

    @SuppressWarnings("unchecked")
    protected List<String> strings(Map<String, Object> source, String property) {
        return (List<String>) source.get(property);
    }

    protected record OpenApiResponse(Map<String, Object> body) {

        OpenApiJsonPath jsonPath() {
            return new OpenApiJsonPath(body);
        }
    }

    protected record OpenApiJsonPath(Map<String, Object> body) {

        Map<String, Object> getMap(String path) {
            if (!"$".equals(path)) {
                throw new IllegalArgumentException("Only the OpenAPI document root is supported");
            }
            return body;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class OpenApiWebTestConfiguration {
    }
}
