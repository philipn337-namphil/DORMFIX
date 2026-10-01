package com.dormfix.location.api;
import com.dormfix.location.application.ResidenceCommandService;
import com.dormfix.location.application.ResidenceQueryService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
@RestController public class ResidenceController {
 private final ResidenceCommandService service; private final ResidenceQueryService queries; public ResidenceController(ResidenceCommandService s, ResidenceQueryService q){service=s;queries=q;}
 @PostMapping("/api/v1/admin/residences") @ResponseStatus(HttpStatus.CREATED) public ResidenceResponse create(@Valid @RequestBody CreateResidenceRequest r,@AuthenticationPrincipal Jwt j){return ResidenceResponse.from(service.create(r.residentId(),r.roomSpaceId(),r.startDate(),id(j),roles(j)));}
 @GetMapping("/api/v1/admin/residences") public ResidencePageResponse list(
         @RequestParam(required=false) Long residentId,@RequestParam(required=false) Long roomSpaceId,
         @RequestParam(required=false) Boolean current,@RequestParam(defaultValue="0") int page,
         @RequestParam(defaultValue="20") int size,@AuthenticationPrincipal Jwt j){
     return ResidencePageResponse.from(queries.listForAdmin(residentId,roomSpaceId,current,page,size,id(j),roles(j)));
 }
 @GetMapping("/api/v1/admin/residences/{id}") public ResidenceResponse adminGet(@PathVariable Long id,@AuthenticationPrincipal Jwt j){return ResidenceResponse.from(queries.findForAdmin(id,id(j),roles(j)));}
 @PostMapping("/api/v1/admin/residences/{id}/end") public ResidenceResponse end(@PathVariable Long id,@AuthenticationPrincipal Jwt j){return ResidenceResponse.from(service.end(id,id(j),roles(j)));}
 @GetMapping("/api/v1/me/residences") public ResidencePageResponse own(
         @RequestParam(required=false) Boolean current,@RequestParam(defaultValue="0") int page,
         @RequestParam(defaultValue="20") int size,@AuthenticationPrincipal Jwt j){
     return ResidencePageResponse.from(queries.own(id(j),roles(j),current,page,size));
 }
 private Long id(Jwt j){try{return Long.valueOf(j.getSubject());}catch(NumberFormatException e){throw new org.springframework.security.access.AccessDeniedException("Access is denied.");}}
 private Set<String> roles(Jwt j){List<String> v=j.getClaimAsStringList("roles"); if(v==null)return Set.of(); return Set.copyOf(v);}
}
