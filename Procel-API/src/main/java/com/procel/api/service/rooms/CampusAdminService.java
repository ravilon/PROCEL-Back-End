package com.procel.api.service.rooms;

import com.procel.api.dto.rooms.CampusDTOs.*;
import com.procel.api.exception.NotFoundException;
import com.procel.api.repository.rooms.CampusRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;

@Service
public class CampusAdminService {
    private final CampusRepository repository;
    public CampusAdminService(CampusRepository repository){this.repository=repository;}
    @Transactional(readOnly=true) public List<CampusResponse> list(){return repository.findAll().stream().map(c->new CampusResponse(c.getNome(),c.getLatitude(),c.getLongitude())).toList();}
    @Transactional public CampusResponse update(String nome,CampusRequest request){
        validate(request); var campus=repository.findById(nome).orElseThrow(()->new NotFoundException("Campus not found nome="+nome));
        campus.setLatitude(request.latitude());campus.setLongitude(request.longitude());repository.save(campus);
        return new CampusResponse(campus.getNome(),campus.getLatitude(),campus.getLongitude());
    }
    private static void validate(CampusRequest r){if(r==null)throw new IllegalArgumentException("body is required");if((r.latitude()==null)!=(r.longitude()==null))throw new IllegalArgumentException("latitude and longitude must be supplied together");range(r.latitude(),new BigDecimal("-90"),new BigDecimal("90"),"latitude");range(r.longitude(),new BigDecimal("-180"),new BigDecimal("180"),"longitude");}
    private static void range(BigDecimal v,BigDecimal min,BigDecimal max,String field){if(v!=null&&(v.compareTo(min)<0||v.compareTo(max)>0))throw new IllegalArgumentException(field+" is out of range");}
}
