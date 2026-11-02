package com.procel.api.service.rooms;

import com.procel.api.entity.rooms.*;
import com.procel.api.repository.rooms.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RoomsIngestionCoordinatesTest {
    @Test
    void cobaltoSynchronizationPreservesExistingCampusCoordinates() {
        CampusRepository campuses=mock(CampusRepository.class);PredioRepository buildings=mock(PredioRepository.class);
        UnidadeRepository units=mock(UnidadeRepository.class);CompartimentoRepository rooms=mock(CompartimentoRepository.class);
        Campus campus=new Campus("Campus");campus.setLatitude(new BigDecimal("-31.770000"));campus.setLongitude(new BigDecimal("-52.340000"));
        when(campuses.findById("Campus")).thenReturn(Optional.of(campus));
        when(units.findById("Unit")).thenReturn(Optional.empty());when(units.save(any())).thenAnswer(i->i.getArgument(0));
        when(buildings.findById(any())).thenReturn(Optional.empty());when(buildings.save(any())).thenAnswer(i->i.getArgument(0));
        when(rooms.findById("42")).thenReturn(Optional.empty());when(rooms.save(any())).thenAnswer(i->i.getArgument(0));

        new RoomsIngestionService(campuses,buildings,units,rooms).ingest(List.of(
                new RoomRecord(42L,"Campus","Building","Unit","Room","classroom",1,20,null,null)));

        assertThat(campus.getLatitude()).isEqualByComparingTo("-31.770000");
        assertThat(campus.getLongitude()).isEqualByComparingTo("-52.340000");
        verify(campuses,never()).save(any());
    }
}
