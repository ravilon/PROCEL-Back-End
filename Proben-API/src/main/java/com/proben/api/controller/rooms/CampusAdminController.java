package com.proben.api.controller.rooms;
import com.proben.api.dto.rooms.CampusDTOs.*;
import com.proben.api.service.rooms.CampusAdminService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/campuses")
public class CampusAdminController {
    private final CampusAdminService service; public CampusAdminController(CampusAdminService service){this.service=service;}
    @GetMapping @PreAuthorize("hasAnyRole('ADMIN','OPERADOR','ANALISTA')") public List<CampusResponse> list(){return service.list();}
    @PutMapping("/{nome}") @PreAuthorize("hasRole('ADMIN')") public CampusResponse update(@PathVariable String nome,@RequestBody CampusRequest request){return service.update(nome,request);}
}
