package com.dormfix.maintenance.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.dormfix.test.TestJwtKeys;
import com.dormfix.maintenance.application.RequestHistoryRepository;
import com.dormfix.maintenance.domain.RequestHistory;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MaintenanceRequestCreationIntegrationTest {
 @Container static final PostgreSQLContainer<?> DB=new PostgreSQLContainer<>("postgres:17.6-alpine");
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("spring.datasource.url",DB::getJdbcUrl);r.add("spring.datasource.username",DB::getUsername);r.add("spring.datasource.password",DB::getPassword);TestJwtKeys.register(r);}
 @Autowired TestRestTemplate http; @Autowired JdbcTemplate jdbc;
 @MockitoSpyBean RequestHistoryRepository historyRepository;
 @Test void residentCreatesRequestWithServerValuesAndHistoryAtomically(){ Fixture f=fixture("ACTIVE"); ResponseEntity<String> response=post(f, payload(f.space(),f.facility(),f.category())); assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);assertThat(response.getHeaders().getLocation().toString()).matches("/api/v1/maintenance-requests/\\d+");assertThat(response.getBody()).contains("DF-").doesNotContain("P-").contains("REPORTED").contains("HIGH"); Long request=jdbc.queryForObject("select id from maintenance_request where reporter_id=?",Long.class,f.resident());assertThat(jdbc.queryForObject("select request_number from maintenance_request where id=?",String.class,request)).isEqualTo("DF-"+request);assertThat(jdbc.queryForObject("select count(*) from request_history where request_id=? and event_type='REQUEST_CREATED' and new_status='REPORTED'",Integer.class,request)).isEqualTo(1); }
 @Test void rejectsWrongRoleCurrentResidenceFacilityAndRetiredFacility(){ Fixture f=fixture("ACTIVE");assertThat(post(new Fixture(f.dorm(),f.space(),f.other(),f.facility(),f.category(),f.resident(),headers(f.resident(),"WORKER")),payload(f.space(),f.facility(),f.category())).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);assertConflict(post(f,payload(f.other(),null,f.category()))); long retired=facility(f.other(),"RETIRED");assertConflict(post(f,payload(f.space(),retired,f.category()))); jdbc.update("update facility set status='RETIRED' where id=?",f.facility());assertConflict(post(f,payload(f.space(),f.facility(),f.category()))); }
 @Test void allowsOutOfServiceButmapsMissingAndMalformed(){ Fixture f=fixture("OUT_OF_SERVICE");assertThat(post(f,payload(f.space(),f.facility(),f.category())).getStatusCode()).isEqualTo(HttpStatus.CREATED);assertThat(post(f,payload(999999L,null,f.category())).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);ResponseEntity<String> bad=post(f,"{\"spaceId\":"+f.space()+",\"categoryId\":"+f.category()+",\"title\":\"x\",\"description\":\"y\",\"entryPolicy\":\"BAD\",\"contactBeforeEntry\":true}");assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST); }
 @Test void missingCategoryAndFacilityUseRepresentativeNotFoundCodes(){ Fixture f=fixture("ACTIVE");ResponseEntity<String> category=post(f,payload(f.space(),null,999999L));assertThat(category.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);assertThat(category.getBody()).contains("CATEGORY_NOT_FOUND");ResponseEntity<String> facility=post(f,payload(f.space(),999999L,f.category()));assertThat(facility.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);assertThat(facility.getBody()).contains("FACILITY_NOT_FOUND"); }
 @Test void inactiveRoomHierarchyAndCategoryAreConflicts(){ Fixture room=fixture("ACTIVE");jdbc.update("update space set active=false where id=?",room.space());assertConflict(post(room,payload(room.space(),room.facility(),room.category())));Fixture building=fixture("ACTIVE");jdbc.update("update building set active=false where id=(select building_id from space where id=?)",building.space());assertConflict(post(building,payload(building.space(),building.facility(),building.category())));Fixture dormitory=fixture("ACTIVE");jdbc.update("update dormitory set active=false where id=?",dormitory.dorm());assertConflict(post(dormitory,payload(dormitory.space(),dormitory.facility(),dormitory.category())));Fixture category=fixture("ACTIVE");jdbc.update("update maintenance_category set active=false where id=?",category.category());assertConflict(post(category,payload(category.space(),category.facility(),category.category()))); }
 @Test void invalidPreferredTimeAndUnknownFieldsAreBadRequest(){ Fixture f=fixture("ACTIVE");String startOnly=payload(f.space(),f.facility(),f.category()).replace(",\"contactBeforeEntry\":true}",",\"contactBeforeEntry\":true,\"preferredVisitStart\":\"2026-10-07T10:00:00Z\"}");assertThat(post(f,startOnly).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);String reversed=payload(f.space(),f.facility(),f.category()).replace(",\"contactBeforeEntry\":true}",",\"contactBeforeEntry\":true,\"preferredVisitStart\":\"2026-10-07T11:00:00Z\",\"preferredVisitEnd\":\"2026-10-07T10:00:00Z\"}");assertThat(post(f,reversed).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);String unknown=payload(f.space(),f.facility(),f.category()).replace("}",",\"unknownField\":true}");assertThat(post(f,unknown).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST); }
 @Test void requestAndHistoryRollbackTogetherWhenHistorySaveFails(){ Fixture f=fixture("ACTIVE");doThrow(new IllegalStateException("history write failed")).when(historyRepository).save(any(RequestHistory.class));ResponseEntity<String> response=post(f,payload(f.space(),f.facility(),f.category()));assertThat(response.getStatusCode().isError()).isTrue();assertThat(jdbc.queryForObject("select count(*) from maintenance_request where reporter_id=?",Integer.class,f.resident())).isZero();assertThat(jdbc.queryForObject("select count(*) from request_history where actor_id=?",Integer.class,f.resident())).isZero(); }
 private void assertConflict(ResponseEntity<String> r){assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);assertThat(r.getBody()).contains("INVALID_REQUEST_STATE");}
 private ResponseEntity<String> post(Fixture f,String body){return http.exchange("/api/v1/maintenance-requests",HttpMethod.POST,new HttpEntity<>(body,f.headers()),String.class);} private String payload(long space,Long facility,long category){return "{\"spaceId\":"+space+",\"facilityId\":"+(facility==null?"null":facility)+",\"categoryId\":"+category+",\"title\":\"Leak\",\"description\":\"Ceiling leak\",\"entryPolicy\":\"RESIDENT_PRESENT_REQUIRED\",\"contactBeforeEntry\":true}";}
 private Fixture fixture(String status){long dorm=dormitory();long room=space(dorm);long other=space(dorm);long resident=user("RESIDENT");jdbc.update("insert into residence(resident_id,room_space_id,start_date) values (?,?,?)",resident,room,java.sql.Date.valueOf(LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1)));return new Fixture(dorm,room,other,facility(room,status),category(),resident,headers(resident,"RESIDENT"));}
 private long dormitory(){String x=key();return jdbc.queryForObject("insert into dormitory(name,address,timezone) values (?,?,'Asia/Seoul') returning id",Long.class,x,x);}private long space(long d){long b=jdbc.queryForObject("insert into building(dormitory_id,code,name) values (?,?,?) returning id",Long.class,d,key(),"B");return jdbc.queryForObject("insert into space(building_id,code,name,type,floor) values (?,?,?,'ROOM',1) returning id",Long.class,b,key(),"R");}private long facility(long s,String status){return jdbc.queryForObject("insert into facility(space_id,name,facility_type,status) values (?,?,'SINK',?) returning id",Long.class,s,key(),status);}private long category(){return jdbc.queryForObject("insert into maintenance_category(code,name,default_priority,sort_order) values (?,?, 'HIGH',1) returning id",Long.class,key(),"Leaks");}private long user(String role){String x=key();long id=jdbc.queryForObject("insert into app_user(email,password_hash,name,status,created_at,updated_at) values (?,'h',?,'ACTIVE',current_timestamp,current_timestamp) returning id",Long.class,x+"@test",x);jdbc.update("insert into user_roles(user_id,role) values (?,?)",id,role);return id;}private String key(){return UUID.randomUUID().toString().substring(0,20);}
 private HttpHeaders headers(long id,String role){HttpHeaders h=new HttpHeaders();h.setContentType(MediaType.APPLICATION_JSON);h.setBearerAuth(token(id,role));return h;}private String token(long id,String role){Instant now=Instant.now();JwtClaimsSet c=JwtClaimsSet.builder().issuer(TestJwtKeys.ISSUER).audience(List.of(TestJwtKeys.AUDIENCE)).subject(Long.toString(id)).issuedAt(now.minusSeconds(1)).notBefore(now.minusSeconds(1)).expiresAt(now.plusSeconds(300)).claim("roles",List.of(role)).build();RSAKey k=new RSAKey.Builder(TestJwtKeys.publicKey()).privateKey(TestJwtKeys.privateKey()).keyID(TestJwtKeys.KEY_ID).algorithm(JWSAlgorithm.RS256).build();JwtEncoder e=new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(k)));return e.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).keyId(TestJwtKeys.KEY_ID).build(),c)).getTokenValue();}
 private record Fixture(long dorm,long space,long other,long facility,long category,long resident,HttpHeaders headers){}
}
