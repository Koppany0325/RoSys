package com.example.rosys.repositories;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;


@Repository
@RequiredArgsConstructor
public class GridRepo {

    private final JdbcTemplate jdbcTemplate;

    public List<Map<String, Object>> getGrids() {
        String sql = "SELECT ST_DumpValues(geom) as values FROM grids";
        return jdbcTemplate.queryForList(sql);
    }

    public Map<String, Object> getScaleAndOriginMap() {

        String sql = """
SELECT 
      ST_UpperLeftX(geom) as origin_x,
      ST_UpperLeftY(geom) as origin_y,
      ST_ScaleX(geom) as scale_x,
      ST_ScaleY(geom) as scale_y
    FROM grids
""";
        return jdbcTemplate.queryForMap(sql);
    }
}
