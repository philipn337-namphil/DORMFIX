package com.dormfix.maintenance.api;
import com.dormfix.maintenance.application.CreateMaintenanceRequestCommand;
import com.dormfix.maintenance.application.MaintenanceRequestCreationService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
@RestController public class MaintenanceRequestController { private final MaintenanceRequestCreationService service; public MaintenanceRequestController(MaintenanceRequestCreationService service){this.service=service;}
 @PostMapping("/api/v1/maintenance-requests") public ResponseEntity<MaintenanceRequestResponse> create(@Valid @RequestBody CreateMaintenanceRequest value,@AuthenticationPrincipal Jwt jwt){var result=service.create(new CreateMaintenanceRequestCommand(value.spaceId(),value.facilityId(),value.categoryId(),value.title(),value.description(),value.entryPolicy(),value.contactBeforeEntry(),value.preferredVisitStart(),value.preferredVisitEnd()),id(jwt),roles(jwt)); return ResponseEntity.created(URI.create("/api/v1/maintenance-requests/"+result.id())).body(MaintenanceRequestResponse.from(result));}
 private Long id(Jwt jwt){try{return Long.valueOf(jwt.getSubject());}catch(NumberFormatException e){throw new AccessDeniedException("Access is denied.");}} private Set<String> roles(Jwt jwt){List<String> values=jwt.getClaimAsStringList("roles");return values==null?Set.of():Set.copyOf(values);} }
