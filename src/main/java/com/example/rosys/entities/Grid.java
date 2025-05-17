package com.example.rosys.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.locationtech.jts.geom.Geometry;


@Entity
@Table(name = "grids")
public class Grid {

    @Id
    private Long id;

    private String name;


    @Column(name = "geom")
    private Geometry geom;
}