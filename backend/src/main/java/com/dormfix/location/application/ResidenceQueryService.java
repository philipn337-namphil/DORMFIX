package com.dormfix.location.application;

import com.dormfix.identity.application.AdminDormitoryScopeRepository;
import com.dormfix.identity.application.DormitoryScopeAccessDeniedException;
import com.dormfix.identity.domain.Role;
import com.dormfix.location.domain.Residence;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResidenceQueryService {
    private final ResidenceRepository residences;
    private final DormitoryScopeResolutionService scopes;
    private final AdminDormitoryScopeRepository adminScopes;

    public ResidenceQueryService(ResidenceRepository residences, DormitoryScopeResolutionService scopes,
            AdminDormitoryScopeRepository adminScopes) { this.residences= residences; this.scopes=scopes; this.adminScopes=adminScopes; }
    @Transactional(readOnly = true)
    public ResidenceResult findForAdmin(Long id, Long actor, Set<String> names) { Residence r=residences.findById(id).orElseThrow(()->new ResidenceNotFoundException(id)); scopes.requireSpaceManageAccess(actor, roles(names), r.getRoomSpaceId()); return result(r); }
    @Transactional(readOnly = true)
    public ResidencePageResult listForAdmin(Long resident, Long room, Boolean current, int page, int size, Long actor, Set<String> names) {
        valid(page,size); if(resident==null&&room==null&&current==null) throw new StructureValidationException("At least one residence filter is required.");
        Set<Role> roles=roles(names); Set<Long> ids;
        boolean allScopes=roles.contains(Role.SUPER_ADMIN); if(allScopes) ids=java.util.Set.of(Long.MIN_VALUE);
        else { if(!roles.contains(Role.ADMIN)) throw new DormitoryScopeAccessDeniedException(); ids=adminScopes.findAllByUserId(actor).stream().map(x->x.getDormitoryId()).collect(java.util.stream.Collectors.toSet()); if(ids.isEmpty() || (room!=null&&!ids.contains(scopes.resolveDormitoryIdForSpace(room)))) throw new DormitoryScopeAccessDeniedException(); }
        var result=residences.findPage(resident,room,current,ids,allScopes,PageRequest.of(page,size)); return new ResidencePageResult(result.getContent().stream().map(this::result).toList(),page,size,result.getTotalElements(),result.getTotalPages()); }
    @Transactional(readOnly = true)
    public ResidencePageResult own(Long user, Set<String> names, Boolean current,int page,int size){valid(page,size);if(!names.contains(Role.RESIDENT.name()))throw new DormitoryScopeAccessDeniedException();var r=residences.findOwnPage(user,current,PageRequest.of(page,size));return new ResidencePageResult(r.getContent().stream().map(this::result).toList(),page,size,r.getTotalElements(),r.getTotalPages());}
    private void valid(int p,int s){if(p<0||s<1||s>100)throw new StructureValidationException("Invalid pagination.");}
    private Set<Role> roles(Set<String> n){try{return n.stream().map(Role::valueOf).collect(java.util.stream.Collectors.toSet());}catch(IllegalArgumentException e){throw new DormitoryScopeAccessDeniedException();}}
    private ResidenceResult result(Residence r){return new ResidenceResult(r.getId(),r.getResidentId(),r.getRoomSpaceId(),r.getStartDate(),r.getEndDate(),r.getCreatedAt());}
}
